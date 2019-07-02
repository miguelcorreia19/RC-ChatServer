import java.io.*;
import java.net.*;
import java.nio.*;
import java.nio.channels.*;
import java.nio.charset.*;
import java.util.*;

public class Server
{
  // A pre-allocated buffer for the received data
  static private final ByteBuffer buffer = ByteBuffer.allocate( 16384 );

  // Decoder for incoming text -- assume UTF-8
  static private final Charset charset = Charset.forName("UTF8");
  static private final CharsetDecoder decoder = charset.newDecoder();

  private static boolean debug = true;

  private static Hashtable<SelectionKey, String> users = new Hashtable<>();
  private static Hashtable<String, LinkedList<SelectionKey>> rooms = new Hashtable<>();
  private static Hashtable<SelectionKey, String> userOnRoom = new Hashtable<>();
  private static Hashtable<SelectionKey, String> userBuffer = new Hashtable<>();

  static public void main( String args[] ) throws Exception {
    // Parse port from command line
    int port = Integer.parseInt( args[0] );
    
    try {
      // Instead of creating a ServerSocket, create a ServerSocketChannel
      ServerSocketChannel ssc = ServerSocketChannel.open();

      // Set it to non-blocking, so we can use select
      ssc.configureBlocking( false );

      // Get the Socket connected to this channel, and bind it to the
      // listening port
      ServerSocket ss = ssc.socket();
      InetSocketAddress isa = new InetSocketAddress( port );
      ss.bind( isa );

      // Create a new Selector for selecting
      Selector selector = Selector.open();

      // Register the ServerSocketChannel, so we can listen for incoming
      // connections
      ssc.register( selector, SelectionKey.OP_ACCEPT );
      System.out.println( "Listening on port " + port );

      while (true) {
        // See if we've had any activity -- either an incoming connection,
        // or incoming data on an existing connection
        int num = selector.select();

        // If we don't have any activity, loop around and wait again
        if (num == 0) {
          continue;
        }

        // Get the keys corresponding to the activity that has been
        // detected, and process them one by one
        Set<SelectionKey> keys = selector.selectedKeys();
        Iterator<SelectionKey> it = keys.iterator();
        while (it.hasNext()) 
        {
          // Get a key representing one of bits of I/O activity
          SelectionKey key = it.next();

          // What kind of activity is it?
          if ((key.readyOps() & SelectionKey.OP_ACCEPT) == SelectionKey.OP_ACCEPT) 
          {
            // It's an incoming connection.  Register this socket with
            // the Selector so we can listen for input on it
            Socket s = ss.accept();
            System.out.println( "Got connection from " + s );

            // Make sure to make it non-blocking, so we can use a selector
            // on it.
            SocketChannel sc = s.getChannel();
            sc.configureBlocking( false );

            // Register it with the selector, for reading
            SelectionKey newKey = sc.register( selector, SelectionKey.OP_READ );

            users.put(newKey,"");
          } 
          else if ((key.readyOps() & SelectionKey.OP_READ) == SelectionKey.OP_READ) 
          {
            SocketChannel sc = null;
            //System.out.println("key = " + key);
            try 
            {
              // It's incoming data on a connection -- process it
              sc = (SocketChannel)key.channel();
              //System.out.println("SocketChannel= " + sc);
              boolean ok = processInput(sc, key);

              // If the connection is dead, remove it from the selector
              // and close it
              if (!ok) 
              {
                userBuffer.remove(key);
                
                String username = users.remove(key);

                String room = userOnRoom.remove(key);
                if(room != null)
                {
                  LinkedList<SelectionKey> usersOnRoom = rooms.get(room);

                  usersOnRoom.remove(key);
                  rooms.put(room,usersOnRoom);
                  roomChatSendMessage("LEFT " + username,usersOnRoom);
                }

                key.cancel();

                Socket s = null;
                try 
                {
                  s = sc.socket();
                  System.out.println( "Closing connection to "+s );
                  s.close();
                } 
                catch( IOException ie ) 
                {
                  System.err.println( "Error closing socket "+s+": "+ie );
                }
              }

            } 
            catch( IOException ie ) 
            {
              // On exception, remove this channel from the selector
              key.cancel();

              try 
              {
                sc.close();
              } 
              catch( IOException ie2 ) { System.out.println( ie2 ); }

              System.out.println( "Closed "+sc );
            }
          }
        }

        // We remove the selected keys, because we've dealt with them.
        keys.clear();
      }
    } catch( IOException ie ) {
      System.err.println( ie );
    }
  }


  // Just read the message from the socket and send it to stdout
  static private boolean processInput(SocketChannel sc , SelectionKey key) throws IOException 
  {
    // Read the message to the buffer
    buffer.clear();
    sc.read( buffer );
    buffer.flip();

    // If no data, close the connection
    if (buffer.limit() == 0) 
    {
      return false;
    }
    // If no data ""
    if (buffer.limit() == 1) 
    {
      return true;
    }

    // Decode and print the message to stdout
    String messageOriginal = decoder.decode(buffer).toString();
    String username = users.get(key);

/*
    System.out.println("\n############ START messageORIGINAL: " + messageOriginal);
    System.out.println("key: " + key);  
    System.out.println("USERNAME: " + username);
*/
    while (messageOriginal.indexOf("\n") != -1) 
    {
      // buffer ?
      if(userBuffer.get(key) != null)
      {
        messageOriginal = userBuffer.get(key) + messageOriginal;
        userBuffer.remove(key);
      }

      int index = messageOriginal.indexOf("\n");
      String message = messageOriginal.substring(0,index);
      messageOriginal = messageOriginal.substring(index+1);
      
      if(message.startsWith("/bye"))
      {
        sc.write(ByteBuffer.wrap("BYE".getBytes("UTF-8"))); 
        return false;
      }

      else if(!message.startsWith("/nick ") && username.equals(""))
      {
        sc.write(ByteBuffer.wrap("ERROR".getBytes("UTF-8")));
        if(debug)
          System.out.println("Erro! Definir username primeiro! /nick \"name\"");
        continue;
      }
     
      else if(message.startsWith("/nick "))
      {
        message = message.substring(new String("/nick ").length());

        String[] words = message.split("\\s+");
        if(words.length > 1 || words.length <= 0)
        {
          sc.write(ByteBuffer.wrap("ERROR".getBytes("UTF-8")));
          if(debug)
            System.out.println("Erro! O username não pode conter caracteres nulos! /nick \"name\"");
          continue;
        } 
        else if(words.length == 1)
        {

          if(username.equals(words[0]))
          {
            sc.write(ByteBuffer.wrap("ERROR".getBytes("UTF-8")));
            if(debug)
              System.out.println("\"" + words[0] + "\" já é o teu username!");
            continue;
          }
          if(users.containsValue(words[0])) // conter na hashmap!
          {
            sc.write(ByteBuffer.wrap("ERROR".getBytes("UTF-8")));
            if(debug)
              System.out.println("O username \"" + words[0] + "\" já está a ser usado! Escolhe outro! /nick \"name\"");
            continue;
          } 

          users.put(key,words[0]);

          String room = userOnRoom.get(key);
          if(room != null)
          {
            System.out.println("NICK SIZE 1 " + rooms.get(room));
            System.out.println(room + " sala " + rooms.size() + " sala-> " + userOnRoom.get(key));

            LinkedList<SelectionKey> usersOnRoom = new LinkedList<>(rooms.get(room));
            System.out.println("NICK SIZE 2 " + rooms.get(room));

            usersOnRoom.remove(key);
            System.out.println("NICK SIZE 3 " + rooms.get(room));
            
            roomChatSendMessage("NEWNICK " + username + " " + words[0],usersOnRoom);
            System.out.println("NICK SIZE 4 " + rooms.get(room));

             System.out.println(room + " salaaaaaaaa " + rooms.size() + " sala-> " + userOnRoom.get(key));

             System.out.println("NICK SIZE MDKANDAWK " + rooms.size());
        System.out.println("NICK SIZE 5  " + rooms.get(room));
          }
           

          if(username.equals(""))
          {
            sc.write(ByteBuffer.wrap("OK".getBytes("UTF-8")));
            if(debug)
              System.out.println("O teu username foi definido! \"" + words[0] + "\"");
            continue;
          }
          
          sc.write(ByteBuffer.wrap("OK".getBytes("UTF-8")));
          if(debug)
            System.out.println("O username foi mudado de " + username + " para " + words[0] + "!");
          continue;
        }
      }

      else if(message.startsWith("/join "))
      {
        message = message.substring(new String("/join ").length());

        String[] words = message.split("\\s+");
        if(words.length > 1 || words.length <= 0)
        {
          sc.write(ByteBuffer.wrap("ERROR".getBytes("UTF-8")));
          if(debug)
            System.out.println("Erro! A sala não pode conter caracteres nulos! /join \"room\"");
          continue;
        } 

        else if(words.length == 1)
        {
          if(rooms.containsKey(words[0]))
          {
            if(userOnRoom.containsKey(key) && userOnRoom.get(key).equals(words[0])) // Já está na sala
            {
              sc.write(ByteBuffer.wrap("ERROR".getBytes("UTF-8")));
              if(debug)
                System.out.println("Já estás na sala " + words[0] + "!");
              continue;
            }

            LinkedList<SelectionKey> usersOnRoom = rooms.get(words[0]);
            roomChatSendMessage("JOINED " + username, usersOnRoom);

            usersOnRoom.add(key);
            rooms.put(words[0],usersOnRoom);

            userOnRoom.put(key,words[0]);

            sc.write(ByteBuffer.wrap("OK".getBytes("UTF-8")));
            if(debug)
              System.out.println("O \"" + username + "\" entrou na sala! \"" + words[0] + "\"");
            continue;
          }

          else
          {
            LinkedList <SelectionKey> usersOnRoom = new LinkedList <SelectionKey> ();
            usersOnRoom.add(key);

            rooms.put(words[0],usersOnRoom);

            userOnRoom.put(key,words[0]);

            sc.write(ByteBuffer.wrap("OK".getBytes("UTF-8")));
            if(debug)
              System.out.println("O \"" + username + "\" criou uma sala! \"" + words[0] + "\"");
            continue;
          }
        }
      }

      else if(message.startsWith("/leave"))
      {
        message = message.substring(new String("/leave").length());

        String[] words = message.split("\\s+");
        if(words.length != 1)//> 0 || words.length < 0)
        {
          sc.write(ByteBuffer.wrap("ERROR".getBytes("UTF-8")));
          if(debug)
            System.out.println("Erro! Para sair da sala é só /leave!");
          continue;
        } 

        else if(words.length == 1)
        {
          String room = userOnRoom.remove(key);
          if(room == null)
          {
            sc.write(ByteBuffer.wrap("ERROR".getBytes("UTF-8")));
            if(debug)
              System.out.println("Erro! O " + username + ", não está numa sala para poder sair!");
            continue;
          }

          LinkedList<SelectionKey> usersOnRoom = rooms.get(room);

          usersOnRoom.remove(key);
          rooms.put(room,usersOnRoom);

          sc.write(ByteBuffer.wrap("OK".getBytes("UTF-8")));
          roomChatSendMessage("LEFT " + username,usersOnRoom);
          if(debug)
            System.out.println("O \"" + username + "\" saiu na sala! \"" + room + "\"");
          continue;
        }
      }

      String room = userOnRoom.get(key);
      if(room != null)
      {
        LinkedList<SelectionKey> usersOnRoom = rooms.get(room);
        System.out.println("SIZE MDKANDAWK " + rooms.size());
        System.out.println("SIZE  " + rooms.get(room));
        
        if(message.startsWith("//"))
          message = message.substring(1, message.length());
          
        roomChatSendMessage("MESSAGE " + username + " " + message,usersOnRoom);
        if(debug)
          System.out.println("O \"" + username + "\" disse " + message + " na \"" + room + "\"!" + usersOnRoom.size());
      }
      else
      {
        sc.write(ByteBuffer.wrap("ERROR".getBytes("UTF-8")));
      }
    }

    if(messageOriginal.length() > 0)
    {
      if(debug)
        System.out.print(username + " Buffering -> " + messageOriginal);
      if(userBuffer.get(key) != null)
        userBuffer.put(key,userBuffer.get(key) + messageOriginal);
      else
        userBuffer.put(key,messageOriginal);
    }

    return true;
  }

  static private void roomChatSendMessage(String message , LinkedList<SelectionKey> usersOnRoom)  throws IOException 
  {
    SocketChannel sc = null;
            
    for(int i = 0; i < usersOnRoom.size(); i++)
    {
      sc = (SocketChannel)usersOnRoom.get(i).channel();
      sc.write(ByteBuffer.wrap((message).getBytes("UTF-8")));

      System.out.println(i);
    }
  }
}
