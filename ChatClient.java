import java.io.*;
import java.net.*;
import java.util.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.nio.*;
import java.awt.*;
import java.awt.event.*;
import javax.swing.*;


public class ChatClient {

    // Variáveis relacionadas com a interface gráfica --- * NÃO MODIFICAR *
    JFrame frame = new JFrame("Chat Client");
    private JTextField chatBox = new JTextField();
    private JTextArea chatArea = new JTextArea();
    
    static private final Charset charset = Charset.forName("UTF8");
    static private final CharsetDecoder decoder = charset.newDecoder();

    // --- Fim das variáveis relacionadas coma interface gráfica

    // Se for necessário adicionar variáveis ao objecto ChatClient, devem
    // ser colocadas aqui

    private static SocketChannel sc;
    static private final ByteBuffer buffer = ByteBuffer.allocate(16384);
    private static String lastMessage = null;


    
    // Método a usar para acrescentar uma string à caixa de texto
    // * NÃO MODIFICAR *
    public void printMessage(final String message) 
    {
        

        chatArea.append(message + "\n");
    }

    // Construtor
    public ChatClient(String server, int port) throws IOException {

        // Inicialização da interface gráfica --- * NÃO MODIFICAR *
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        JPanel panel = new JPanel();
        panel.setLayout(new BorderLayout());
        panel.add(chatBox);
        frame.setLayout(new BorderLayout());
        frame.add(panel, BorderLayout.SOUTH);
        frame.add(new JScrollPane(chatArea), BorderLayout.CENTER);
        frame.setSize(500, 300);
        frame.setVisible(true);
        chatArea.setEditable(false);
        chatBox.setEditable(true);
        chatBox.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                try {
                    newMessage(chatBox.getText());
                } catch (IOException ex) {
                } finally {
                   chatBox.setText("");
                }
            }
        });

        // Instead of creating a ServerSocket, create a ServerSocketChannel
        InetSocketAddress isa = new InetSocketAddress( port );
        sc = SocketChannel.open(isa);

        // Set it to non-blocking, so we can use select
        sc.configureBlocking( true );
    }


    // Método invocado sempre que o utilizador insere uma mensagem
    // na caixa de entrada
    public void newMessage(String message) throws IOException 
    {
        // PREENCHER AQUI com código que envia a mensagem ao servidor

        if(message.startsWith("/") && !message.startsWith("/nick ") && !message.equals("/leave") && !message.equals("/bye") && !message.startsWith("/join "))
        {
            sc.write(ByteBuffer.wrap(("/" + message + "\n").getBytes("UTF-8")));
        }
        else
        {
            sc.write(ByteBuffer.wrap((message + "\n").getBytes("UTF-8")));
        }

        lastMessage = message;
    }
    
    // Método principal do objecto
    public void run() throws IOException 
    {
        // PREENCHER AQUI

        while (true) 
        {
            // Read the message to the buffer
            buffer.clear();
            sc.read( buffer );
            buffer.flip();

            String message = decoder.decode(buffer).toString();
            if(message.equals("BYE")) 
            {
                sc.socket().close();
                frame.dispose(); 
                return;
            }
            printMessage(message);
        }
    }

    // Instancia o ChatClient e arranca-o invocando o seu método run()
    // * NÃO MODIFICAR *
    public static void main(String[] args) throws IOException 
    {
        ChatClient client = new ChatClient(args[0], Integer.parseInt(args[1]));
        client.run();
    }

}
