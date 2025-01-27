package client;

import com.sun.mail.pop3.POP3Folder;
import com.sun.mail.pop3.POP3Message;
import com.sun.mail.pop3.POP3Store;
import com.sun.mail.smtp.SMTPSSLTransport;
import org.apache.commons.io.output.CountingOutputStream;
import org.apache.commons.io.output.NullOutputStream;

import javax.mail.*;

import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.InetAddress;
import java.security.SecureRandom;
import java.util.*;

public class JavaMail {
    /**
     * Singleton instance
     */
    private static JavaMail instance;

    /**
     * JavaMail-Properties
     */
    private final Properties properties = new Properties();
    /**
     * JavaMail-Session
     */
    private Session session;
    /**
     * Folder of the specific chosen inbox from the mail server
     */
    private POP3Folder emailInbox;
    /**
     * Array of the fetched messages from the inbox.
     */
    private Message[] messages;

//    private Transport transport = new SMTPSSLTransport(session, null);


    private MimeMessage mimeMessage;

    private Authenticator auth;

    JavaMail() {
    }

    /**
     * United Instance
     *
     * @return Static instance
     */
    public static JavaMail getInstance() {
        if (instance == null) {
            instance = new JavaMail();
            return instance;
        }
        return instance;
    }

    /**
     * Sets the property-values
     *
     * @param host   Domain of the mail-server
     * @param port   Port of the mail-server
     * @param secure {@code True} flag if connection is supposed to be secure, otherwise {@code false}.
     */
    public void initConnectProperties(String host, String port, boolean secure) {
        properties.put("mail.pop3.host", host);
        properties.put("mail.pop3.port", port);
        properties.put("mail.pop3.connectiontimeout", "100");

        if (secure) {
            properties.put("mail.store.protocol", "pop3");
            properties.put("mail.pop3.ssl.enable", "true");
            properties.put("mail.pop3.starttls.enable", "true");
            properties.put("mail.pop3.ssl.socketFactory.class", "SSLSocket");
        } else {
            properties.put("mail.store.protocol", "pop3");
            properties.put("mail.pop3.ssl.enable", "false");
            properties.put("mail.pop3.starttls.enable", "false");
        }
    }

    public boolean authenticateSMTP( String username , String password  ) throws MessagingException {
        try {

            Transport transport = Session.getInstance( properties ).getTransport();
            transport.connect( username,password );
            transport.close();
            return true;
        } catch ( AuthenticationFailedException e ) {
            return false;
        }
    }


    public void initConnectPropertiesSmtp(String host, String port, boolean secure) {
        properties.put("mail.smtp.host", host);
        properties.put("mail.smtp.auth", "true");

        if (secure) {
           // properties.put("mail.smtp.ssl.enable", "true");  //
            properties.put("mail.smtp.socketFactory.port", port);
            properties.put("mail.smtp.starttls.enable", "true");
            properties.put("mail.smtp.socketFactory.class",
                    "javax.net.ssl.SSLSocketFactory");

        } else {
            properties.put("mail.smtp.port", port);
            properties.put("mail.smtp.ssl.enable", "false");
            properties.put("mail.smtp.starttls.enable", "false");
        }
    }

    /**
     * Connects to the Mail-server.
     *
     * @param username Username
     * @param password Password
     * @throws AuthenticationFailedException If an authentication error occurred while signing in with username/password.
     * @throws MessagingException            If Connection-error occurred.
     */
    public void connect(String username, String password) throws AuthenticationFailedException, MessagingException {
        session = Session.getDefaultInstance(properties);
        POP3Store store = (POP3Store) session.getStore((String) properties.get("mail.store.protocol"));

        store.connect(username, password);

        emailInbox = (POP3Folder) store.getFolder("INBOX");
        emailInbox.open(Folder.READ_ONLY);
    }


    public void connectSmtp(String username, String password) throws NoSuchProviderException, MessagingException {


        auth = new Authenticator() {
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(username, password);
            }
        };

        session = Session.getInstance(properties, auth);


    }


        /*session = Session.getDefaultInstance(properties, new Authenticator() {
                    protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(username,password);  }

        });*/


    public void setMessageHeaders() {
        try {
            mimeMessage.addHeader("Content-type", "text/plain; charset=UTF-8");
            mimeMessage.addHeader("format", "flowed");
            mimeMessage.addHeader("Content-Transfer-Encoding", "8bit");

        } catch (MessagingException e) {
            System.out.println("problem while setting Message Headers");
        }

    }


    public boolean sendmail(String from, String subject, String body,String name, Address ... recipients) throws UnsupportedEncodingException, MessagingException {

        try {


            mimeMessage = new MimeMessage(session);
            mimeMessage.setFrom(new InternetAddress(from, name));
            mimeMessage.setReplyTo(InternetAddress.parse(from, false));
            mimeMessage.addRecipients(Message.RecipientType.TO, recipients);
            mimeMessage.setSubject(subject);
            mimeMessage.setText(body);
            setMessageHeaders();
            mimeMessage.setSentDate(new Date());
            Transport.send(mimeMessage);


        } catch (MessagingException e) {
            e.printStackTrace();
            return false;
        }

        return true;
    }






    /**
     * Fetch messages
     *
     * @throws MessagingException If Connection-error occurred.
     */
    public void updateMessages() throws MessagingException {
        messages = emailInbox.getMessages();

        FetchProfile fetchProfile = new FetchProfile();
        fetchProfile.add(FetchProfile.Item.CONTENT_INFO);
        fetchProfile.add("X-mailer");

        emailInbox.fetch(messages, fetchProfile);
    }

    /**
     * Reads a specific message
     *
     * @param messageSegment Message object to read.
     * @return String containing the contents of the message
     * @throws MessagingException If Connection-error occurred.
     * @throws IOException        If Connection-error occurred.
     */
    public String readMessage(Part messageSegment) throws MessagingException, IOException {
        StringBuilder sb = new StringBuilder();
        readMessageUtil(messageSegment, sb);

        new SecureRandom().nextInt();

        return sb.toString();
    }

    /**
     * Utility-Method to fetch the message
     *
     * @param messageSegment Message object to read.
     * @param sb             String-builder to use.
     * @throws MessagingException If Connection-error occurred.
     * @throws IOException        If Connection-error occurred.
     */
    private void readMessageUtil(Part messageSegment, StringBuilder sb) throws MessagingException, IOException {
        if (messageSegment instanceof Message) sb.append(getMessageEnvelope((Message) messageSegment));

        //plain text message
        if (messageSegment.isMimeType("text/plain")) {
            sb.append("\n").append((String) messageSegment.getContent()).append("\n");
        }
        //Check multipart message
        else if (messageSegment.isMimeType("multipart/*")) {
            Multipart multipart = (Multipart) messageSegment.getContent();
            for (int i = 0; i < multipart.getCount(); i++) {
                readMessageUtil(multipart.getBodyPart(i), sb);
            }
        }
        //check if the content is a nested message
        else if (messageSegment.isMimeType("message/rfc822")) {
            sb.append("Nested-Message:").append("\n");
            readMessageUtil((Part) messageSegment.getContent(), sb);
        }
        //read image attachment
        else if (messageSegment.isMimeType("image/jpeg")) {
            InputStream is = (InputStream) messageSegment.getContent();
            byte[] buffer = is.readAllBytes();
            FileOutputStream fos = new FileOutputStream("output.jpg");
            fos.write(buffer);
            fos.close();
        }
    }

    /**
     * Get the envelope of the message.
     *
     * @param message Message to inspect
     * @return A String containing the envelope data.
     * @throws MessagingException If Connection-error occurred.
     */
    private String getMessageEnvelope(Message message) throws MessagingException {
        StringBuilder builder = new StringBuilder();

        Address[] addresses;

        if ((addresses = message.getFrom()) != null) for (Address address : addresses)
            builder.append("From:").append(" ").append(address.toString()).append("\n");

        if ((addresses = message.getRecipients(Message.RecipientType.TO)) != null) for (Address address : addresses)
            builder.append("To:").append(" ").append(address.toString()).append("\n");

        if (message.getSubject() != null) builder.append("Subject: ").append(message.getSubject()).append("\n");

        if (message.getDescription() != null)
            builder.append("Description: ").append(message.getDescription()).append("\n");

        return builder.toString();
    }


    /**
     * @return List containing the Messages.
     */
    public List<Message> getMessages() {
        return Arrays.asList(messages);
    }

    public POP3Folder getEmailInbox() {
        return emailInbox;
    }
}
