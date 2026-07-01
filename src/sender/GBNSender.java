package sender;

import protocol.Packet;
import protocol.PacketType;
import protocol.ProtocolConfig;
import util.ArgumentParser.SenderArguments;
import util.FileUtils;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

public class GBNSender {

    private final SenderArguments senderArgs;
    private final SenderStats stats = new SenderStats();

    private DatagramSocket socket;
    private InetAddress receiverAddress;
    private Packet[] packets;
    private int totalPackets;

    private int base;
    private int nextSeqNum;
    private int windowSize;

    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    private ScheduledFuture<?> timerFuture;
    private final Object lock = new Object();

    private volatile boolean running = true;
    private Thread ackReceiverThread;

    public GBNSender(SenderArguments senderArgs) {
        this.senderArgs = senderArgs;
    }

    public void start() throws Exception {
        receiverAddress = InetAddress.getByName(senderArgs.getDestinationIp());
        windowSize = senderArgs.getWindowSize();

        try (DatagramSocket ds = new DatagramSocket()) {
            this.socket = ds;

            stats.startTimer();

            sendHandshake();
            socket.setSoTimeout(200);
            preparePackets();
            startAckReceiver();
            sendFileGoBackN();
            stopAckReceiver();
            sendFinWithAck();

            String originalHash = util.HashUtils.calculateMD5(senderArgs.getSourceFilePath());
            System.out.println("MD5 do arquivo original: " + originalHash);

            stats.stopTimer();
            stats.printStats();
        } finally {
            scheduler.shutdownNow();
        }
    }

    private void sendHandshake() throws Exception {
        long fileSize = FileUtils.getFileSize(senderArgs.getSourceFilePath());
        String originalHash = util.HashUtils.calculateMD5(senderArgs.getSourceFilePath());

        String handshakeMessage = senderArgs.getLossProbability() + ";" +
                senderArgs.getDestinationPath() + ";" +
                fileSize + ";" +
                originalHash;

        Packet handshakePacket = Packet.createHandshakePacket(handshakeMessage.getBytes());
        byte[] data = handshakePacket.toBytes();

        DatagramPacket datagram = new DatagramPacket(
                data,
                data.length,
                receiverAddress,
                senderArgs.getDestinationPort()
        );

        socket.send(datagram);

        System.out.println("Handshake enviado. Aguardando ACK...");

        byte[] buffer = new byte[Packet.MAX_PACKET_SIZE];
        DatagramPacket ackDatagram = new DatagramPacket(buffer, buffer.length);

        socket.receive(ackDatagram);
        Packet ackPacket = Packet.fromBytes(ackDatagram.getData(), ackDatagram.getLength());

        if (ackPacket.getType() == PacketType.ACK) {
            System.out.println("ACK do handshake recebido.");
        } else {
            throw new IllegalStateException("Resposta inesperada do receptor.");
        }
    }

    private void preparePackets() throws Exception {
        byte[] fileData = FileUtils.readFile(senderArgs.getSourceFilePath());
        totalPackets = (int) Math.ceil((double) fileData.length / Packet.MAX_PAYLOAD_SIZE);
        packets = new Packet[totalPackets];

        for (int i = 0; i < totalPackets; i++) {
            int offset = i * Packet.MAX_PAYLOAD_SIZE;
            int chunkSize = Math.min(Packet.MAX_PAYLOAD_SIZE, fileData.length - offset);
            byte[] chunk = new byte[chunkSize];
            System.arraycopy(fileData, offset, chunk, 0, chunkSize);
            packets[i] = Packet.createDataPacket(i, chunk);
        }
    }

    private void startAckReceiver() {
        AckReceiver ackReceiver = new AckReceiver(socket, this);
        ackReceiverThread = new Thread(ackReceiver);
        ackReceiverThread.start();
    }

    private void stopAckReceiver() throws InterruptedException {
        running = false;
        if (ackReceiverThread != null) {
            ackReceiverThread.join(500);
        }
    }

    private void sendFileGoBackN() throws Exception {
        base = 0;
        nextSeqNum = 0;

        while (base < totalPackets) {
            sendNextPackets();
            Thread.sleep(10);
        }

        stopTimer();
        System.out.println("Todos os pacotes foram confirmados.");
    }

    private void sendNextPackets() throws Exception {
        synchronized (lock) {
            while (nextSeqNum < base + windowSize && nextSeqNum < totalPackets) {
                Packet packet = packets[nextSeqNum];
                sendPacket(packet);

                stats.incrementPacketsSent();
                stats.addBytesSent(packet.getDataLength());

                System.out.println("Pacote DATA enviado. Seq = " + nextSeqNum);

                if (base == nextSeqNum) {
                    startTimer();
                }

                nextSeqNum++;
            }
        }
    }

    public void handleAck(Packet ackPacket) {
        synchronized (lock) {
            int ackNumber = ackPacket.getAckNumber();

            if (ackNumber >= base && ackNumber < totalPackets) {
                System.out.println("ACK recebido. Ack = " + ackNumber);
                stats.incrementAcksReceived();

                base = ackNumber + 1;

                if (base == nextSeqNum || base >= totalPackets) {
                    stopTimer();
                } else {
                    startTimer();
                }
            }
        }
    }

    private void startTimer() {
        stopTimer();
        timerFuture = scheduler.schedule(this::timeout, ProtocolConfig.TIMEOUT_MS, TimeUnit.MILLISECONDS);
    }

    private void stopTimer() {
        if (timerFuture != null) {
            timerFuture.cancel(false);
            timerFuture = null;
        }
    }

    private void timeout() {
        synchronized (lock) {
            if (base >= totalPackets) {
                return;
            }

            System.out.println("TIMEOUT! Retransmitindo de " + base + " ate " + (nextSeqNum - 1));

            for (int i = base; i < nextSeqNum && i < totalPackets; i++) {
                try {
                    sendPacket(packets[i]);
                    stats.incrementPacketsSent();
                    stats.incrementRetransmissions();
                    stats.addBytesSent(packets[i].getDataLength());
                    System.out.println("Pacote retransmitido. Seq = " + i);
                } catch (Exception e) {
                    System.err.println("Erro ao retransmitir pacote " + i + ": " + e.getMessage());
                }
            }

            timerFuture = scheduler.schedule(this::timeout, ProtocolConfig.TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }

    private void sendFinWithAck() throws Exception {
        socket.setSoTimeout(ProtocolConfig.TIMEOUT_MS);

        Packet finPacket = Packet.createFinPacket();
        byte[] finBytes = finPacket.toBytes();
        DatagramPacket finDatagram = new DatagramPacket(
                finBytes,
                finBytes.length,
                receiverAddress,
                senderArgs.getDestinationPort()
        );

        int attempts = 0;
        while (attempts < 5) {
            socket.send(finDatagram);
            System.out.println("Pacote FIN enviado. Tentativa " + (attempts + 1));

            try {
                byte[] buffer = new byte[Packet.MAX_PACKET_SIZE];
                DatagramPacket ackDatagram = new DatagramPacket(buffer, buffer.length);
                socket.receive(ackDatagram);

                Packet ackPacket = Packet.fromBytes(ackDatagram.getData(), ackDatagram.getLength());
                if (ackPacket.getType() == PacketType.ACK && ackPacket.getAckNumber() == totalPackets) {
                    System.out.println("ACK do FIN recebido.");
                    break;
                }
            } catch (SocketTimeoutException e) {
                attempts++;
                System.out.println("Timeout do FIN, retransmitindo...");
            }
        }

        System.out.println("Transmissao finalizada pelo emissor.");
    }

    private void sendPacket(Packet packet) throws Exception {
        byte[] packetBytes = packet.toBytes();
        DatagramPacket datagram = new DatagramPacket(
                packetBytes,
                packetBytes.length,
                receiverAddress,
                senderArgs.getDestinationPort()
        );
        socket.send(datagram);
    }

    public boolean isRunning() {
        return running;
    }
}
