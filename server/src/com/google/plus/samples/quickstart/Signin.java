/*
 * Copyright 2013 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.plus.samples.quickstart;

import com.google.api.client.auth.oauth2.TokenResponseException;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeTokenRequest;
import com.google.api.client.googleapis.auth.oauth2.GoogleClientSecrets;
import com.google.api.client.googleapis.auth.oauth2.GoogleCredential;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.GenericUrl;
import com.google.api.client.http.HttpResponse;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.jackson.JacksonFactory;
import com.google.api.services.plus.Plus;
import com.google.api.services.plus.model.PeopleFeed;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.google.gdata.data.ILink;

import org.mortbay.jetty.Server;
import org.mortbay.jetty.servlet.ServletHandler;
import org.mortbay.jetty.servlet.SessionHandler;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.math.BigInteger;
import java.security.SecureRandom;
import java.util.Scanner;
import java.net.URL;
import java.util.*;
import java.net.URLEncoder;
import java.io.PrintWriter;
import java.net.HttpURLConnection;
import java.io.DataOutputStream;


import com.google.gdata.client.*;
import com.google.gdata.client.photos.*;
import com.google.gdata.data.*;
import com.google.gdata.data.media.*;
import com.google.gdata.data.photos.*;
import com.google.gdata.util.ServiceException;
import com.google.gdata.client.http.AuthSubUtil;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.net.ssl.HttpsURLConnection;

/**
 * Simple server to demonstrate how to use Google+ Sign-In and make a request
 * via your own server.
 *
 * @author joannasmith@google.com (Joanna Smith)
 * @author vicfryzel@google.com (Vic Fryzel)
 */

public class Signin {
  /*
   * Default HTTP transport to use to make HTTP requests.
   */
  private static final HttpTransport TRANSPORT = new NetHttpTransport();

  /*
   * Default JSON factory to use to deserialize JSON.
   */
  private static final JacksonFactory JSON_FACTORY = new JacksonFactory();

  /*
   * Gson object to serialize JSON responses to requests to this servlet.
   */
  private static final Gson GSON = new Gson();

  /*
   * Creates a client secrets object from the client_secrets.json file.
   */
  private static GoogleClientSecrets clientSecrets;

  static {
    try {
      Reader reader = new FileReader("client_secrets.json");
      clientSecrets = GoogleClientSecrets.load(JSON_FACTORY, reader);
    } catch (IOException e) {
      throw new Error("No client_secrets.json found", e);
    }
  }

  /*
   * This is the Client ID that you generated in the API Console.
   */
  private static final String CLIENT_ID = clientSecrets.getWeb().getClientId();

  /*
   * This is the Client Secret that you generated in the API Console.
   */
  private static final String CLIENT_SECRET = clientSecrets.getWeb().getClientSecret();

  /*
   * Optionally replace this with your application's name.
   */
  private static final String APPLICATION_NAME = "Smart Home";

  /**
   * Register all endpoints that we'll handle in our server.
   * @param args Command-line arguments.
   * @throws Exception from Jetty if the component fails to start
   */
  public static void main(String[] args) throws Exception {
    Server server = new Server(4567);
    ServletHandler servletHandler = new ServletHandler();
    SessionHandler sessionHandler = new SessionHandler();
    sessionHandler.setHandler(servletHandler);
    server.setHandler(sessionHandler);
    servletHandler.addServletWithMapping(ConnectServlet.class, "/connect");
    servletHandler.addServletWithMapping(DisconnectServlet.class, "/disconnect");
    servletHandler.addServletWithMapping(PeopleServlet.class, "/people");
    servletHandler.addServletWithMapping(AlbumServlet.class, "/album");
    servletHandler.addServletWithMapping(MainServlet.class, "/");
    servletHandler.addServletWithMapping(SearchServlet.class, "/search");
    servletHandler.addServletWithMapping(ResultsServlet.class, "/res");
    server.start();
    server.join();
  }

  /**
   * Initialize a session for the current user, and render index.html.
   */
  public static class MainServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
      // This check prevents the "/" handler from handling all requests by default
      if (!"/".equals(request.getServletPath())) {
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        return;
      }

      response.setContentType("text/html");
      try {
        // Create a state token to prevent request forgery.
        // Store it in the session for later validation.
        String state = new BigInteger(130, new SecureRandom()).toString(32);
        request.getSession().setAttribute("state", state);
        // Fancy way to read index.html into memory, and set the client ID
        // and state values in the HTML before serving it.
        response.getWriter().print(new Scanner(new File("index.html"), "UTF-8")
            .useDelimiter("\\A").next()
            .replaceAll("[{]{2}\\s*CLIENT_ID\\s*[}]{2}", CLIENT_ID)
            .replaceAll("[{]{2}\\s*STATE\\s*[}]{2}", state)
            .replaceAll("[{]{2}\\s*APPLICATION_NAME\\s*[}]{2}",
                APPLICATION_NAME)
            .toString());
        response.setStatus(HttpServletResponse.SC_OK);
      } catch (FileNotFoundException e) {
        // When running the quickstart, there was some path issue in finding
        // index.html.  Double check the quickstart guide.
        e.printStackTrace();
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.getWriter().print(e.toString());
      }
    }
  }

  /**
   * Upgrade given auth code to token, and store it in the session.
   * POST body of request should be the authorization code.
   * Example URI: /connect?state=...&gplus_id=...
   */
  public static class ConnectServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
      response.setContentType("application/json");

      // Only connect a user that is not already connected.
      String tokenData = (String) request.getSession().getAttribute("token");
      if (tokenData != null) {
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().print(GSON.toJson("Current user is already connected."));
        return;
      }
      // Ensure that this is no request forgery going on, and that the user
      // sending us this connect request is the user that was supposed to.
      if (!request.getParameter("state").equals(request.getSession().getAttribute("state"))) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().print(GSON.toJson("Invalid state parameter."));
        return;
      }
      // Normally the state would be a one-time use token, however in our
      // simple case, we want a user to be able to connect and disconnect
      // without reloading the page.  Thus, for demonstration, we don't
      // implement this best practice.
      //request.getSession().removeAttribute("state");

      ByteArrayOutputStream resultStream = new ByteArrayOutputStream();
      getContent(request.getInputStream(), resultStream);
      String code = new String(resultStream.toByteArray(), "UTF-8");

      try {
        // Upgrade the authorization code into an access and refresh token.
        GoogleTokenResponse tokenResponse =
            new GoogleAuthorizationCodeTokenRequest(TRANSPORT, JSON_FACTORY,
                CLIENT_ID, CLIENT_SECRET, code, "postmessage").execute();

        // You can read the Google user ID in the ID token.
        // This sample does not use the user ID.
        GoogleIdToken idToken = tokenResponse.parseIdToken();
        String gplusId = idToken.getPayload().getSubject();

        // Store the token in the session for later use.
        request.getSession().setAttribute("token", tokenResponse.toString());
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().print(GSON.toJson("Successfully connected user."));
      } catch (TokenResponseException e) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().print(GSON.toJson("Failed to upgrade the authorization code."));
      } catch (IOException e) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().print(GSON.toJson("Failed to read token data from Google. " +
            e.getMessage()));
      }
    }

    /*
     * Read the content of an InputStream.
     *
     * @param inputStream the InputStream to be read.
     * @return the content of the InputStream as a ByteArrayOutputStream.
     * @throws IOException
     */
    static void getContent(InputStream inputStream, ByteArrayOutputStream outputStream)
        throws IOException {
      // Read the response into a buffered stream
      BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
      int readChar;
      while ((readChar = reader.read()) != -1) {
        outputStream.write(readChar);
      }
      reader.close();
    }
  }

  /**
   * Revoke current user's token and reset their session.
   */
  public static class DisconnectServlet extends HttpServlet {
    @Override
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
      response.setContentType("application/json");

      // Only disconnect a connected user.
      String tokenData = (String) request.getSession().getAttribute("token");
      if (tokenData == null) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().print(GSON.toJson("Current user not connected."));
        return;
      }
      try {
        // Build credential from stored token data.
        GoogleCredential credential = new GoogleCredential.Builder()
            .setJsonFactory(JSON_FACTORY)
            .setTransport(TRANSPORT)
            .setClientSecrets(CLIENT_ID, CLIENT_SECRET).build()
            .setFromTokenResponse(JSON_FACTORY.fromString(
                tokenData, GoogleTokenResponse.class));
        // Execute HTTP GET request to revoke current token.
        HttpResponse revokeResponse = TRANSPORT.createRequestFactory()
            .buildGetRequest(new GenericUrl(
                String.format(
                    "https://accounts.google.com/o/oauth2/revoke?token=%s",
                    credential.getAccessToken()))).execute();
        // Reset the user's session.
        request.getSession().removeAttribute("token");
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().print(GSON.toJson("Successfully disconnected."));
      } catch (IOException e) {
        // For whatever reason, the given token was invalid.
        response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        response.getWriter().print(GSON.toJson("Failed to revoke token for given user."));
      }
    }
  }

  /**
   * Get list of people user has shared with this app.
   */
  public static class PeopleServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
      response.setContentType("application/json");

      // Only fetch a list of people for connected users.
      String tokenData = (String) request.getSession().getAttribute("token");
      if (tokenData == null) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().print(GSON.toJson("Current user not connected."));
        return;
      }
      try {
        // Build credential from stored token data.
        GoogleCredential credential = new GoogleCredential.Builder()
            .setJsonFactory(JSON_FACTORY)
            .setTransport(TRANSPORT)
            .setClientSecrets(CLIENT_ID, CLIENT_SECRET).build()
            .setFromTokenResponse(JSON_FACTORY.fromString(
                tokenData, GoogleTokenResponse.class));
        // Create a new authorized API client.
        Plus service = new Plus.Builder(TRANSPORT, JSON_FACTORY, credential)
            .setApplicationName(APPLICATION_NAME)
            .build();
        // Get a list of people that this user has shared with this app.
        PeopleFeed people = service.people().list("me", "visible").execute();
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().print(GSON.toJson(people));
      } catch (IOException e) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().print(GSON.toJson("Failed to read data from Google. " +
            e.getMessage()));
      }
    }
  }

  public static class AlbumServlet extends HttpServlet {
    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {
      response.setContentType("text/html");

      // Only fetch a list of people for connected users.
      String tokenData = (String) request.getSession().getAttribute("token");
      if (tokenData == null) {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.getWriter().print(GSON.toJson("Current user not connected."));
        return;
      }
	String page = "<html>\n<head>\n<title>Smart Home</title>\n<body><p>";
	String USER_AGENT = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/37.0.2062.120 Safari/537.36";
	String user = null;
	String token = null;
	String uurl = null;
	String allbums = null;
	String bla = "";
	String fluck = null;
	ArrayList<String> stock_list = new ArrayList<String>();
		List<GphotoEntry> entries = null;
		List<GphotoEntry> entries1 = null;
		List<AlbumEntry> myAlbums = new ArrayList<AlbumEntry>();
		List<PhotoEntry> photos = new ArrayList<PhotoEntry>();
		List <String> luck1 = new ArrayList<String>();
		List <String> luck = new ArrayList<String>();
		List <String> fuck = new ArrayList<String>();
		List<CommentEntry> comments = new ArrayList<CommentEntry>();
		List<TagEntry> tags = new ArrayList<TagEntry>();
      try {
        // Build credential from stored token data.
        GoogleCredential credential = new GoogleCredential.Builder()
            .setJsonFactory(JSON_FACTORY)
            .setTransport(TRANSPORT)
            .setClientSecrets(CLIENT_ID, CLIENT_SECRET).build()
            .setFromTokenResponse(JSON_FACTORY.fromString(
                tokenData, GoogleTokenResponse.class));
        // Create a new authorized API client.
		Plus service = new Plus.Builder(TRANSPORT, JSON_FACTORY, credential)
            		.setApplicationName(APPLICATION_NAME)
		        .build();
		PicasawebService myService = new PicasawebService(APPLICATION_NAME);
      		user = service.people().get("me").execute().getId();
		token = credential.getAccessToken();
		myService.setAuthSubToken(token, null);
		uurl = String.format("https://picasaweb.google.com/data/feed/api/user/%1$s?kind=album&access=all&token=%2$s", user, token);
		URL feedUrl = new URL(uurl);
		UserFeed myUserFeed = null;
		entries =  myService.getFeed(feedUrl, UserFeed.class).getEntries();
		for (GphotoEntry entry : entries) /*albumId*/
				fuck.add(entry.getGphotoId());
		entries = null;
		for (String ss : fuck){
			String next = String.format("http://picasaweb.google.com/data/feed/api/user/%1$s/albumid/%2$s?token=%3$s", user, ss, token);
			URL furl = new URL(next);
			entries1 = myService.getFeed(furl, UserFeed.class).getEntries();
			for (GphotoEntry entry1 : entries1) { /*PhotoId*/
				GphotoEntry adapted = entry1.getAdaptedEntry();
					luck.add(entry1.getGphotoId()); /*imageitself*/
					fluck = String.format("https://picasaweb.google.com/data/feed/api/user/%1$s/albumid/%2$s/photoid/%3$s?imgmax=1600&token=%4$s",
									user, ss, entry1.getGphotoId(), token);
					String sPhotoId = entry1.getId();
					PhotoEntry p = new PhotoEntry(entry1);
					String sPhotoUrl = p.getMediaContents().get(0).getUrl();
					int slashIndex = sPhotoUrl.lastIndexOf("/");
					sPhotoUrl = sPhotoUrl.substring(0, slashIndex) + "/s1600" + sPhotoUrl.substring(slashIndex);
					bla += sPhotoUrl + " ";
					Process pro = Runtime.getRuntime().exec("wget " + sPhotoUrl + " -nH --cut-dirs=4 -nc -P /home/ubuntu/Users/" + user);
					page += "<img src=\"" + sPhotoUrl + "\" /><br />\n";
					stock_list.add(sPhotoUrl);
			}/* for entries*/

		}
	Process pro = Runtime.getRuntime().exec("mkdir /home/ubuntu/Users/" + user + "/isolated");
	page += "</p>\n</body>\n</html>";
	String[] stockArr = new String[stock_list.size()];
	stockArr = stock_list.toArray(stockArr);
	response.setStatus(HttpServletResponse.SC_OK);
	response.getWriter().println(page.trim());
      } catch (IOException e) {
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().print(GSON.toJson("Failed to read data from Google. " +
            e.getMessage()));
      } catch (Exception e){
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.getWriter().print(GSON.toJson("Google. " +
            e.getMessage() + " bla:" + bla + " tags: " + tags + " comme: " + comments + " url: " + fluck + " allbums:"
			   + myAlbums + " entries: " + entries + " luck1: " + luck1 + " photos: "+ photos));
    }
    }
  }

  public static class SearchServlet extends HttpServlet {
	@Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {


      response.setContentType("text/html");
      String user = null;
      String query ="";
      query = (String) request.getAttribute("query");
      String tokenData = (String) request.getSession().getAttribute("token");

      try {
        // Create a state token to prevent request forgery.
        // Store it in the session for later validation.
        response.getWriter().print(new Scanner(new File("search.html"), "UTF-8")
            .useDelimiter("\\A").next()
            .replaceAll("[{]{2}\\s*APPLICATION_NAME\\s*[}]{2}",
                APPLICATION_NAME)
            .toString());
        response.setStatus(HttpServletResponse.SC_OK);
      } catch (FileNotFoundException e) {
        // When running the quickstart, there was some path issue in finding
        // index.html.  Double check the quickstart guide.
        e.printStackTrace();
        response.setStatus(HttpServletResponse.SC_NOT_FOUND);
        response.getWriter().print(e.toString());
      }
    }
  }

  static class ResultsServlet extends HttpServlet {
        @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException {


      response.setContentType("text/html");
      String user = null;
      String tokenData = (String) request.getSession().getAttribute("token");
      String query ="";
      try{
      query = request.getParameter("query");

      if (query != null){
        // Build credential from stored token data.
        GoogleCredential credential = new GoogleCredential.Builder()
            .setJsonFactory(JSON_FACTORY)
            .setTransport(TRANSPORT)
            .setClientSecrets(CLIENT_ID, CLIENT_SECRET).build()
            .setFromTokenResponse(JSON_FACTORY.fromString(
                tokenData, GoogleTokenResponse.class));
        // Create a new authorized API client.
        Plus service = new Plus.Builder(TRANSPORT, JSON_FACTORY, credential)
                        .setApplicationName(APPLICATION_NAME)
                        .build();
        user = service.people().get("me").execute().getId();
        String page = "<html>\n<head>\n<title>Smart Home</title>\n<body><p>";
        //Process pro = Runtime.getRuntime().exec("sudo python /home/ubuntu/Helios/java_wrapper.py " + user + "-s " + query);
        page += query + user + "</p>\n</body>\n</html>";
        response.setStatus(HttpServletResponse.SC_OK);
        response.getWriter().println(page.trim());
      }
    } catch (IOException e) {
	response.setStatus(HttpServletResponse.SC_BAD_GATEWAY);
	String page = "<html>\n<head>\n<title>Smart Home</title>\n<body><p>";
	page += query + user + "</p>\n</body>\n</html>";
	response.getWriter().println(page.trim());
      }
    }
  }
}
