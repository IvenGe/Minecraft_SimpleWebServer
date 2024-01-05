package be.dailysurvival.webserver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.util.HashMap;

public class Connection  implements Runnable {
    private final FileConfiguration config;
    // The socket used to connect to the server
    private Socket connectionSocket;
    // A HashMap to store the keys/values inside of the client request
    private HashMap<String, String> request;
    // A HashMap that contains keys that need to be redirected to the given value
    private HashMap<String, String> redirect;

    /**
     * Creates a Connection object
     *
     * @param connectionSocket The socket the client used to connect to the server
     * @param config
     */
    public Connection(Socket connectionSocket, FileConfiguration config) {
        this.config = config;

        this.connectionSocket = connectionSocket;

        this.request = new HashMap<>();
        this.redirect = new HashMap<>();

        // Add key/value pairs to the redirect HashMap
        // The key represents the URL value used by the user and the value is the URL value to redirect to
        redirect.put("/", "/index.html");
        redirect.put("/index.htm", "/index.html");
        redirect.put("/index", "/index.html");
    }

    /**
     *
     * Parses the client request and inserts all the request fields into the
     * request HashMap. The key is the request field while the value is the value
     * of the field.
     *
     * @throws IOException if the connectionSocket is not present and the its inputStream is inaccessible
     *
     */
    private void parseRequest() throws IOException {

        // Connect a BufferedReader to the client socket's input stream and read it its request data
        BufferedReader connectionReader = new BufferedReader(new InputStreamReader(connectionSocket.getInputStream()));

        // Read in the top client request line. ex: GET /index.html HTTP/1.1
        String requestLine = connectionReader.readLine();

        // If the request line exists we begin to parse the request, otherwise it is not a proper request
        // and we do nothing
        if (requestLine != null) {

            // The top line of a client request is formatted differently from the rest of the request so
            // we get the values of the top line of the client request first
            String[] requestLineParams = requestLine.split(" ");

            // Extract the relevant information from the top line of the request. Method, Resource URL, and Protocol
            String requestMethod = requestLineParams[0];
            String requestResource = requestLineParams[1];
            String requestProtocol = requestLineParams[2];

            // Add the Method, Resource, and Protocol to the request HashMap
            request.put("Method", requestMethod);
            request.put("Resource", requestResource);
            request.put("Protocol", requestProtocol);

            // Read the next line of the client request header
            String headerLine = connectionReader.readLine();

            // While the request header still has lines to read we continue reading and
            // storing the values of each request field into the request HashMap
            while (!headerLine.isEmpty()) {

                // Splits the request field into its key and value pair
                String[] requestParams = headerLine.split(":", 2);

                // Put the request field key and value into the request HashMap
                request.put(requestParams[0], requestParams[1].replaceFirst(" ", ""));

                // Read the next header line of the request
                headerLine = connectionReader.readLine();
            }
        }
    }

    /**
     * Sends the appropriate response based on the client request.
     *
     * If the URL requested is inside of the redirect HashMap
     * the client is sent a HTTP 301 error redirect response, sending the client to
     * the new URL location. If the URL requested is not inside of the redirect HashMap
     * and does not exist an HTTP 404 error response is sent. If the URL request
     * exists an HTTP 200 OK response is sent.
     *
     * @throws IOException if outStream, fileStream, or bufInputStream is closed or does not exist
     * while they are being used.
     *
     */
    private void sendResponse() throws IOException {

        // Create an DataOutputStream, outStream, to be able to send information out to the client connection
        DataOutputStream outStream = new DataOutputStream(connectionSocket.getOutputStream());

        // Get the file path of the file requested by the client connection and open the requested file
        String resourcePath = request.get("Resource").toString();
        File file = new File( "./plugins/WebServer/" +config.getString("WebSiteFolder")  + resourcePath);


        // If the file requested by the client is in the redirect HashMap then send the client a
        // HTTP 301 response and redirect the client to the new file address
        if (redirect.get(resourcePath) != null) {

            // Send the client a HTTP 301 request and redirect to new address
            outStream.writeBytes("HTTP/1.1 301 Moved Permanently\n" +
                    "Location: " + redirect.get(resourcePath));
        }

        // If the file requested does not exist send the client a HTTP 404 response with a webpage
        // that tells the client that the file was not found
        else if (!file.exists()) {

            // Create HTTP 404 response with a basic webpage
            String http404Response = "HTTP/1.1 404 Not Found\r\n\r\n" + "<!DOCTYPE html>\n" +
                    "<html>\n" +
                    "\n" +
                    "<head>\n" +
                    "    <title>Not Found</title>\n" +
                    "</head>\n" +
                    "\n" +
                    "<body><h1>\n" +
                    "404 Error: file not found\n" +
                    "</h1></body>\n" +
                    "\n" +
                    "</html>";

            // Send the HTTP 404 response to the client using the UTF-8 encoding
            outStream.write(http404Response.getBytes("UTF-8"));
        }

        // If the file is not in the redirect HashMap and it exists then we send the client
        // a HTTP 200 response and serve the file
        else {

            // Open a file input stream to read data from the file
            FileInputStream fileStream = new FileInputStream(file);

            // Get the MIME file type of the file that the client is requesting
            String contentType = Files.probeContentType(file.toPath());

            // Create a BufferedInputStream to read data from the fileStream
            BufferedInputStream bufInputStream = new BufferedInputStream(fileStream);

            // Create an array of bytes the same length of the file requested to hold the file data bytes
            byte[] bytes = new byte[(int) file.length()];

            // Send the header of the HTTP 200 response
            outStream.writeBytes("HTTP/1.1 200 OK\r\nContent-Type: " + contentType + "\r\n\r\n");

            // Read in the data from the file requested into the bytes array
            bufInputStream.read(bytes);

            // Send the data contained by the bytes array to the client connection and flush the output stream
            outStream.write(bytes);
            outStream.flush();

            // Close the input stream
            bufInputStream.close();
        }
        // Close the output stream
        outStream.close();
    }

    /**
     * Ran at the start of the runnable Connection object's execution inside of a thread
     */
    @Override
    public void run() {
        try (Socket socket = this.connectionSocket) {
            // Parse the client request and store the request field keys/values inside of the request HashMap
            parseRequest();

            // Send an appropriate response to the client based on the request received by the server
            sendResponse();
        } catch (IOException ex) {
            // Handle or log the exception appropriately
            //ex.printStackTrace();
        }
    }
}
