package receiver;

public class ReceiverStats {

    private int packetsReceived;
    private int inOrderPacketsReceived;
    private int artificialLosses;
    private int outOfOrderPackets;

    public void incrementPacketsReceived() {
        packetsReceived++;
    }

    public void incrementInOrderPacketsReceived() {
        inOrderPacketsReceived++;
    }

    public void incrementArtificialLosses() {
        artificialLosses++;
    }

    public void incrementOutOfOrderPackets() {
        outOfOrderPackets++;
    }

    public void printStats() {
        double effectiveLossRate = inOrderPacketsReceived == 0
                ? 0
                : ((double) artificialLosses / inOrderPacketsReceived) * 100;

        System.out.println();
        System.out.println("===== ESTATISTICAS DO RECEPTOR =====");
        System.out.println("Pacotes DATA recebidos........: " + packetsReceived);
        System.out.println("Pacotes em ordem recebidos....: " + inOrderPacketsReceived);
        System.out.println("Perdas artificiais............: " + artificialLosses);
        System.out.println("Pacotes fora de ordem.........: " + outOfOrderPackets);
        System.out.printf("Taxa de perda efetiva.........: %.2f%%\n", effectiveLossRate);
    }
}
