package sender;

import protocol.Packet;
import protocol.PacketType;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketTimeoutException;

public class AckReceiver implements Runnable {

    private final DatagramSocket socket;
    private final GBNSender sender;
    private volatile boolean running = true;

    public AckReceiver(DatagramSocket socket, GBNSender sender) {
        this.socket = socket;
        this.sender = sender;
    }

    public void stop() {
        running = false;
    }

    @Override
    public void run() {
        while (running && sender.isRunning()) {
            try {
                byte[] buffer = new byte[Packet.MAX_PACKET_SIZE];
                DatagramPacket datagram = new DatagramPacket(buffer, buffer.length);
                socket.receive(datagram);

                Packet packet = Packet.fromBytes(datagram.getData(), datagram.getLength());

                if (packet.getType() == PacketType.ACK) {
                    sender.handleAck(packet);
                }
            } catch (SocketTimeoutException e) {
                // Timeout curto apenas para verificar flag de execucao
            } catch (Exception e) {
                if (running) {
                    System.err.println("Erro no AckReceiver: " + e.getMessage());
                }
            }
        }
    }
}
