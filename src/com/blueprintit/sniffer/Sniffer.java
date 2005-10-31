package com.blueprintit.sniffer;

import java.nio.channels.SocketChannel;
import java.nio.channels.ServerSocketChannel;
import java.nio.ByteBuffer;
import java.nio.channels.Selector;
import java.nio.channels.SelectionKey;
import java.net.InetSocketAddress;
import java.util.Iterator;
import java.net.InetSocketAddress;

public class Sniffer implements Runnable
{
	private Selector selector;
	private ByteBuffer buffer;
	
	public Sniffer() throws Exception
	{
		buffer = ByteBuffer.allocate(128);
		selector = Selector.open();
		(new Thread(this)).start();
	}

	public void addListener(InetSocketAddress local, InetSocketAddress remote)
	{
		try
		{
			ServerSocketChannel newl = ServerSocketChannel.open();
			newl.socket().bind(local);
			newl.configureBlocking(false);
			newl.register(selector,SelectionKey.OP_ACCEPT,remote);
			newl.accept();
			log("Listener added");
		}
		catch (Exception e)
		{
			log("Error adding listener",e);
		}
	}
	
	private void log(String message)
	{
		System.err.println(message);
	}
	
	private void log(String message, Throwable e)
	{
		System.err.println(message);
		e.printStackTrace();
	}
	
	private void newConnection(SelectionKey key)
	{
		try
		{
			SocketChannel local = ((ServerSocketChannel)key.channel()).accept();
			local.configureBlocking(false);
			InetSocketAddress address = (InetSocketAddress)key.attachment();
			SocketChannel remote = SocketChannel.open(address);
			remote.configureBlocking(false);
			local.register(selector,SelectionKey.OP_READ,remote);
			remote.register(selector,SelectionKey.OP_READ,local);
			log("New connection");
		}
		catch (Exception e)
		{
			log("Error handling a new connection",e);
		}
	}
	
	private void newData(SelectionKey key)
	{
		int count;
		try
		{
			count=((SocketChannel)key.channel()).read(buffer);
		}
		catch (Exception e)
		{
			count=-1;
		}
		if (count>=0)
		{
			buffer.flip();
			SocketChannel remote = (SocketChannel)key.attachment();
			try
			{
				remote.write(buffer);
			}
			catch (Exception e)
			{
				log("Error writing to socket",e);
			}
			buffer.clear();
		}
		else
		{
			try
			{
				((SocketChannel)key.attachment()).close();
			}
			catch (Exception e)
			{
			}
			key.cancel();
		}
	}
	
	public void run()
	{
		try
		{
			while (true)
			{
				selector.select();
				Iterator loop = selector.selectedKeys().iterator();
				while (loop.hasNext())
				{
					SelectionKey key = (SelectionKey)loop.next();
					loop.remove();
					try
					{
						if (key.isAcceptable())
						{
							newConnection(key);
						}
						else if (key.isReadable())
						{
							newData(key);
						}
					}
					catch (Throwable e)
					{
						log("Exception handling io: ",e);
						e.printStackTrace();
						key.cancel();
						key.channel().close();
					}
				}
			}
		}
		catch (Exception e)
		{
		}
	}
	
	public static void main(String[] args) throws Exception
	{
		Sniffer sniffer = new Sniffer();
		sniffer.addListener(new InetSocketAddress(8888),new InetSocketAddress("eeguinness.swan.ac.uk",80));
		System.out.println("Finished setup");
	}
}
