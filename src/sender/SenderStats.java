package sender;

public class SenderStats {

    private int packetsSent;
    private int retransmissions;
    private int acksReceived;
    private long startTime;
    private long endTime;
    private long bytesSent;

    public void startTimer() {
        startTime = System.currentTimeMillis();
    }

    public void stopTimer() {
        endTime = System.currentTimeMillis();
    }

    public void incrementPacketsSent() {
        packetsSent++;
    }

    public void incrementRetransmissions() {
        retransmissions++;
    }

    public void incrementAcksReceived() {
        acksReceived++;
    }

    public void addBytesSent(int bytes) {
        bytesSent += bytes;
    }

    public void printStats() {
        long durationMs = endTime - startTime;
        double durationSeconds = durationMs / 1000.0;
        double throughput = durationSeconds > 0 ? bytesSent / durationSeconds : 0;

        System.out.println("\n=== ESTATÍSTICAS DO EMISSOR ===");
        System.out.println("Pacotes enviados: " + packetsSent);
        System.out.println("Retransmissões: " + retransmissions);
        System.out.println("ACKs recebidos: " + acksReceived);
        System.out.println("Bytes enviados: " + bytesSent);
        System.out.println("Tempo total: " + durationMs + " ms");
        System.out.printf("Throughput estimado: %.2f bytes/s%n", throughput);
    }
}