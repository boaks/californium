/********************************************************************************
 * Copyright (c) 2025 Contributors to the Eclipse Foundation
 * 
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 * 
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License v. 2.0 which is available at
 * https://www.eclipse.org/legal/epl-2.0, or the Eclipse Distribution License
 * v1.0 which is available at
 * https://www.eclipse.org/org/documents/edl-v10.php.
 * 
 * SPDX-License-Identifier: EPL-2.0 OR BSD-3-Clause
 ********************************************************************************/
package org.eclipse.californium.core.coap.option;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.fail;

import org.eclipse.californium.core.coap.CoAP.ResponseCode;
import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;
import org.eclipse.californium.core.coap.option.TransmissionCountOption.Definition;
import org.eclipse.californium.core.network.serialization.UdpDataSerializer;
import org.eclipse.californium.elements.category.Small;
import org.eclipse.californium.elements.rule.TestNameLoggerRule;
import org.eclipse.californium.elements.util.DatagramReader;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;

/**
 * Tests the functionality of the class TransmissionCountOption.
 * 
 * @since 4.0
 */
@Category(Small.class)
public class TransmissionCountOptionTest {

	public static final Definition DEFINITION = TransmissionCountOption.DEFINITION;

	@Rule
	public TestNameLoggerRule name = new TestNameLoggerRule();

	@Test
	public void testCreate() {
		byte[] bytes = { 0x04 };
		DatagramReader reader = new DatagramReader(bytes);

		TransmissionCountOption test = DEFINITION.create(2);
		assertThat(test, is(notNullValue()));
		assertThat(test.getIntegerValue(), is(2));

		test = DEFINITION.create(reader, 1);
		assertThat(test, is(notNullValue()));
		assertThat(test.getIntegerValue(), is(4));
	}

	@Test(expected = NullPointerException.class)
	public void testCreateWithoutReader() {
		DEFINITION.create(null, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCreateWithReaderTooLarge() {
		DatagramReader reader = new DatagramReader("0123456789".getBytes());
		DEFINITION.create(reader, 7);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCreateTooLarge() {
		DEFINITION.create(500);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCreateWithReaderEmpty() {
		DatagramReader reader = new DatagramReader("test".getBytes());
		DEFINITION.create(reader, 0);
	}

	@Test(expected = IllegalArgumentException.class)
	public void testCreateEmpty() {
		DEFINITION.create(0);
	}

	@Test
	public void testIncrement() {
		Request request = Request.newGet();
		request.setMID(1);
		UdpDataSerializer serializer = new UdpDataSerializer();

		serializer.ensureByteArray(request);
		assertThat(request.getBytes(), is(notNullValue()));

		boolean done = TransmissionCountOption.increment(request);

		// increment without option
		assertThat(done, is(false));

		request.getOptions().addOtherOption(DEFINITION.create(1));
		// increment with option
		done = TransmissionCountOption.increment(request);
		assertThat(done, is(true));
		TransmissionCountOption option = request.getOptions().getOtherOption(DEFINITION);
		assertThat(option, is(notNullValue()));
		assertThat(option.getIntegerValue(), is(2));
		assertThat(request.getBytes(), is(nullValue()));
	}

	@Test(expected = NullPointerException.class)
	public void testIncrementFailsWithNull() {
		TransmissionCountOption.increment(null);
	}

	@Test
	public void testIncrementFailsWithOverflow() {
		Request request = Request.newGet();
		request.getOptions().addOtherOption(DEFINITION.create(255));
		try {
			TransmissionCountOption.increment(request);
			fail("Overflow not detected");
		} catch (IllegalArgumentException ex) {
			// expected
		}
	}

	@Test
	public void testTranscribe() {
		Request request = Request.newGet();
		Response response = new Response(ResponseCode.CONTENT);
		response.setMID(0);
		response.setType(Type.ACK);
		UdpDataSerializer serializer = new UdpDataSerializer();

		serializer.ensureByteArray(response);
		assertThat(response.getBytes(), is(notNullValue()));

		// transcribe without option
		boolean done = TransmissionCountOption.transcribe(request, response);
		assertThat(done, is(false));
		TransmissionCountOption option = response.getOptions().getOtherOption(DEFINITION);
		assertThat(option, is(nullValue()));

		// transcribe with option
		request.getOptions().setOtherOption(DEFINITION.create(1));
		done = TransmissionCountOption.transcribe(request, response);
		assertThat(done, is(true));
		option = response.getOptions().getOtherOption(DEFINITION);
		assertThat(option, is(notNullValue()));
		assertThat(option.getIntegerValue(), is(1));
		assertThat(response.getBytes(), is(nullValue()));

		serializer.ensureByteArray(response);
		assertThat(response.getBytes(), is(notNullValue()));

		// transcribe with different option
		request.getOptions().setOtherOption(DEFINITION.create(2));
		done = TransmissionCountOption.transcribe(request, response);
		assertThat(done, is(true));
		option = response.getOptions().getOtherOption(DEFINITION);
		assertThat(option, is(notNullValue()));
		assertThat(option.getIntegerValue(), is(2));
		assertThat(response.getBytes(), is(nullValue()));

		// transcribe with observe request
		request.getOptions().setObserve(1);
		done = TransmissionCountOption.transcribe(request, response);
		response = new Response(ResponseCode.CONTENT);
		assertThat(done, is(false));
		option = response.getOptions().getOtherOption(DEFINITION);
		assertThat(option, is(nullValue()));
	}

	@Test(expected = NullPointerException.class)
	public void testTranscribeWithNullRequest() {
		Response response = new Response(ResponseCode.CONTENT);
		TransmissionCountOption.transcribe(null, response);
	}

	@Test(expected = NullPointerException.class)
	public void testTranscribeWithNullResponse() {
		Request request = Request.newGet();
		TransmissionCountOption.transcribe(request, null);
	}

}
