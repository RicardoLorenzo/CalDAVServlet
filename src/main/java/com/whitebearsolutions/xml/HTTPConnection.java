package com.whitebearsolutions.xml;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.HashMap;

public class HTTPConnection
{

	public static final String ACL = "ACL";
	public static final String DELETE = "DELETE";
	public static final String MKCOL = "MKCOL";
	public static final String MKCALENDAR = "MKCALENDAR";
	public static final String PROPFIND = "PROPFIND";
	public static final String PUT = "PUT";
	public static final String REPORT = "REPORT";
	private String hostname;
	private String userAgent;
	private HashMap<String, String> headers = new HashMap<String, String>();
	private int port;
	private String path;
	private String contentType;
	private String content;
	private int responseCode;
	private String responseMessage;

	public HTTPConnection(String hostName)
	{
		this.hostname = hostName;
	}

	public void setUserAgent(String userAgent)
	{
		this.userAgent = userAgent;

	}

	public void connect(String method) throws IOException
	{
		URL url = new URL("http", hostname, port, URLEncoder.encode(path, "UTF-8"));
		HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();
		urlConnection.setRequestMethod(method);
		urlConnection.setDoInput(true);
		urlConnection.setRequestProperty("Content-Type", contentType);
		urlConnection.setRequestProperty("User-Agent", userAgent);

		for (String header : this.headers.keySet())
		{
			urlConnection.setRequestProperty(header, this.headers.get(header));
		}

		OutputStreamWriter out = new OutputStreamWriter(urlConnection.getOutputStream());
		out.write(this.content);
		out.close();
		urlConnection.getInputStream();
		this.responseCode = urlConnection.getResponseCode();
		this.responseMessage = urlConnection.getResponseMessage();
	}

	public byte[] getContent()
	{
		return this.content.getBytes();
	}

	public int getResponseCode()
	{
		return this.responseCode;
	}

	public String getResponseMessage()
	{
		return this.responseMessage;
	}

	public String getServer()
	{
		return hostname;
	}

	public void setPort(int port)
	{
		this.port = port;

	}

	public void setContent(String content)
	{
		this.content = content;

	}

	public void setContentType(String contentType)
	{
		this.contentType = contentType;

	}

	public void setHeader(String key, String value)
	{
		this.headers.put(key, value);

	}

	public void setPath(String path)
	{
		this.path = path;

	}

	public void setUser(String user, String password)
	{
		// TODO Auto-generated method stub

	}

}
