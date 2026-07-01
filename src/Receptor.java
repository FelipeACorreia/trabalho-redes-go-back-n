import protocol.ProtocolConfig;
import receiver.GBNReceiver;

public class Receptor {

    public static void main(String[] args) {
        try {
            int port = ProtocolConfig.DEFAULT_RECEIVER_PORT;

            if (args.length > 0) {
                port = Integer.parseInt(args[0]);
            }

            System.out.println("=== RECEPTOR GBN ===");

            GBNReceiver receiver = new GBNReceiver(port);
            receiver.start();

        } catch (NumberFormatException e) {
            System.err.println("Porta invalida. Use: java Receptor [porta]");
        } catch (Exception e) {
            System.err.println("Erro no receptor: " + e.getMessage());
        }
    }
}
