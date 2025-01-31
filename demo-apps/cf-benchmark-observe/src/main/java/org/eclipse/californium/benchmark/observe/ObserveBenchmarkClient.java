/*******************************************************************************
 * Copyright (c) 2015 Institute for Pervasive Computing, ETH Zurich and others.
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v2.0
 * and Eclipse Distribution License v1.0 which accompany this distribution.
 * 
 * The Eclipse Public License is available at
 *    http://www.eclipse.org/legal/epl-v20.html
 * and the Eclipse Distribution License is available at
 *    http://www.eclipse.org/org/documents/edl-v10.html.
 * 
 * Contributors:
 *    Matthias Kovatsch - creator and main architect
 *    Martin Lanter - architect and initial implementation
 *    Martin Dzieżyc - implementation of observable resources
 ******************************************************************************/

package org.eclipse.californium.benchmark.observe;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

import org.eclipse.californium.core.CoapServer;
import org.eclipse.californium.core.config.CoapConfig;
import org.eclipse.californium.core.network.CoapEndpoint;
import org.eclipse.californium.elements.config.Configuration;
import org.eclipse.californium.elements.config.UdpConfig;
import org.eclipse.californium.elements.util.ExecutorsUtil;
import org.eclipse.californium.elements.util.NamedThreadFactory;
import org.eclipse.californium.elements.util.ProtocolScheduledExecutorService;

public class ObserveBenchmarkClient {
	public static final int CORES = Runtime.getRuntime().availableProcessors();
	public static final String OS = System.getProperty("os.name");
	public static final boolean WINDOWS = OS.startsWith("Windows");

	public static final String DEFAULT_ADDRESS = null;
	public static final int DEFAULT_PORT = 5683;

	public static final int DEFAULT_PROTOCOL_STAGE_THREAD_COUNT = CORES;

	public static final int DEFAULT_SENDER_COUNT = WINDOWS ? CORES : 1;
	public static final int DEFAULT_RECEIVER_COUNT = WINDOWS ? CORES : 1;

	static {
		CoapConfig.register();
	}

	public static void main(String[] args) throws Exception {
		System.out.println("Californium (Cf) Observe Benchmark Client");
		System.out.println("(c) 2015, Institute for Pervasive Computing, ETH Zurich");
		System.out.println();
		System.out.println("This machine has " + CORES + " cores");
		System.out.println("Operating system: " + OS);

		String address = null;
		int port = DEFAULT_PORT;
		int udp_sender = DEFAULT_SENDER_COUNT;
		int udp_receiver = DEFAULT_RECEIVER_COUNT;
		int protocol_threads = DEFAULT_PROTOCOL_STAGE_THREAD_COUNT;
		boolean use_executor = false;

		// Parse input
		if (args.length > 0) {
			int index = 0;
			while (index < args.length) {
				String arg = args[index];
				if ("-usage".equals(arg) || "-help".equals(arg) || "-h".equals(arg) || "-?".equals(arg)) {
					printUsage();
				} else if ("-t".equals(arg)) {
					protocol_threads = Integer.parseInt(args[index + 1]);
				} else if ("-s".equals(arg)) {
					udp_sender = Integer.parseInt(args[index + 1]);
				} else if ("-r".equals(arg)) {
					udp_receiver = Integer.parseInt(args[index + 1]);
				} else if ("-p".equals(arg)) {
					port = Integer.parseInt(args[index + 1]);
				} else if ("-a".equals(arg)) {
					address = args[index + 1];
				} else if ("-use-executor".equals(arg)) {
					use_executor = true;
				} else {
					System.err.println("Unknown arg " + arg);
					printUsage();
				}
				index += 2;
			}
		}

		// Parse address
		InetAddress addr = address != null ? InetAddress.getByName(address) : null;
		InetSocketAddress sockAddr = new InetSocketAddress((InetAddress) addr, port);

		setBenchmarkConfiguration(udp_sender, udp_receiver);

		// Create server
		CoapServer server = new CoapServer();
		ProtocolScheduledExecutorService executor = ExecutorsUtil.newProtocolScheduledThreadPool(protocol_threads, new NamedThreadFactory("observer-benchmark"));
		if (use_executor) {
			System.out.println("Using a scheduled thread pool with " + protocol_threads + " workers");
			server.setExecutor(executor, false);
		}

		System.out.println("Number of receiver/sender threads: " + udp_receiver + "/" + udp_sender);

		server.add(new AnnounceResource("announce", executor));

		CoapEndpoint.Builder builder = new CoapEndpoint.Builder();
		builder.setInetSocketAddress(sockAddr);
		server.addEndpoint(builder.build());
		server.start();

		System.out.println("Observe benchmark announcement server listening on " + sockAddr);
	}

	private static void setBenchmarkConfiguration(int udp_sender, int udp_receiver) {

		// Configuration optimal for performance benchmarks
		Configuration.createStandardWithoutFile()
				// Disable deduplication OR strongly reduce lifetime
				.set(CoapConfig.DEDUPLICATOR, CoapConfig.NO_DEDUPLICATOR)
				.set(CoapConfig.EXCHANGE_LIFETIME, 1500, TimeUnit.MILLISECONDS)

				// Increase buffer for network interface to 10 MB
				.set(UdpConfig.UDP_RECEIVE_BUFFER_SIZE, 10 * 1024 * 1024)
				.set(UdpConfig.UDP_SEND_BUFFER_SIZE, 10 * 1024 * 1024)

				// Increase threads for receiving and sending packets through the socket
				.set(UdpConfig.UDP_RECEIVER_THREAD_COUNT, udp_receiver)
				.set(UdpConfig.UDP_SENDER_THREAD_COUNT, udp_sender);
	}

	private static void printUsage() {
		System.out.println();
		System.out.println("SYNOPSIS");
		System.out.println("	" + ObserveBenchmarkClient.class.getSimpleName()
				+ " [-a ADDRESS] [-p PORT] [-t POOLSIZE] [-s SENDERS] [-r RECEIVERS]");
		System.out.println("OPTIONS");
		System.out.println("	-a ADDRESS");
		System.out.println(
				"		Bind the server to a specific host IP address given by ADDRESS (default is wildcard address).");
		System.out.println("	-p PORT");
		System.out.println("		Listen on UDP port PORT (default is " + DEFAULT_PORT + ").");
		System.out.println("	-t POOLSIZE");
		System.out.println("		Use POOLSIZE worker threads in the endpoint (default is the number of cores).");
		System.out.println("	-s SENDERS");
		System.out.println("		Use SENDERS threads to copy messages to the UDP socket.");
		System.out.println("		The default is number of cores on Windows and 1 otherwise.");
		System.out.println("	-r RECEIVERS");
		System.out.println("		Use RECEIVERS threads to copy messages from the UDP socket.");
		System.out.println("		The default is number of cores on Windows and 1 otherwise.");
		System.out.println("    -use-workers");
		System.out.println(
				"        Use a specialized queue for incoming requests that reduces synchronization of threads.");
		System.out.println("OPTIMIZATIONS");
		System.out.println("	-Xms4096m -Xmx4096m");
		System.out.println("		Set the Java heap size to 4 GiB.");
		System.out.println("EXAMPLES");
		System.out.println(
				"	java -Xms4096m -Xmx4096m " + ObserveBenchmarkClient.class.getSimpleName() + " -p 5684 -t 16");
		System.out.println(
				"	java -Xms4096m -Xmx4096m -jar " + ObserveBenchmarkClient.class.getSimpleName() + ".jar -s 2 -r 2");
		System.exit(0);
	}
}
