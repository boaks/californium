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
 *    Martin Lanter - architect and re-implementation
 *    Dominique Im Obersteg - parsers and initial implementation
 *    Daniel Pauli - parsers and initial implementation
 *    Kai Hudalla - logging
 ******************************************************************************/
package org.eclipse.californium.core.coap.option;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import org.eclipse.californium.elements.category.Small;
import org.eclipse.californium.elements.rule.TestNameLoggerRule;
import org.eclipse.californium.elements.util.DatagramReader;
import org.eclipse.californium.elements.util.DatagramWriter;
import org.eclipse.californium.elements.util.StringUtil;
import org.junit.Rule;
import org.junit.Test;
import org.junit.experimental.categories.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Tests of the functionality of the class BlockOption. BlockOption
 * converts the parameters SZX, M, NUM (defined in the draft) to a byte array
 * and extracts these parameters vice-versa form a specified byte array.
 */
@Category(Small.class)
public class BlockOptionTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(BlockOptionTest.class);

	@Rule
	public TestNameLoggerRule name = new TestNameLoggerRule();

	/**
	 * Verifies that conversion from block size to szx code works.
	 */
	@Test
	public void testComputeSzxReturnsNextSmallerSize() {
		assertThat(BlockOption.size2Szx(1600), is(6));
		assertThat(BlockOption.size2Szx(1024), is(6));
		assertThat(BlockOption.size2Szx(540), is(5));
		assertThat(BlockOption.size2Szx(512), is(5));
		assertThat(BlockOption.size2Szx(400), is(4));
		assertThat(BlockOption.size2Szx(256), is(4));
		assertThat(BlockOption.size2Szx(170), is(3));
		assertThat(BlockOption.size2Szx(128), is(3));
		assertThat(BlockOption.size2Szx(90), is(2));
		assertThat(BlockOption.size2Szx(64), is(2));
		assertThat(BlockOption.size2Szx(33), is(1));
		assertThat(BlockOption.size2Szx(32), is(1));
		assertThat(BlockOption.size2Szx(25), is(0));
		assertThat(BlockOption.size2Szx(16), is(0));
	}

	/**
	 * Verifies that block size < 16 is mapped to szx 0.
	 */
	@Test
	public void testComputeSzxReturnsMinSize() {
		assertThat(BlockOption.size2Szx(8), is(0));
	}

	/**
	 * Verifies that conversion from szx codes to block size works.
	 */
	@Test
	public void testGetSizeForSzx() {
		assertThat(BlockOption.szx2Size(-1), is(16));
		assertThat(BlockOption.szx2Size(0), is(16));
		assertThat(BlockOption.szx2Size(1), is(32));
		assertThat(BlockOption.szx2Size(2), is(64));
		assertThat(BlockOption.szx2Size(3), is(128));
		assertThat(BlockOption.szx2Size(4), is(256));
		assertThat(BlockOption.szx2Size(5), is(512));
		assertThat(BlockOption.szx2Size(6), is(1024));
		assertThat(BlockOption.szx2Size(8), is(1024));
	}

	/**
	 * Tests that the class BlockOption converts the specified parameters to the
	 * correct byte array
	 */
	@Test
	public void testGetValue() {
		assertArrayEquals(toBytes(0, false, 0), b());
		assertArrayEquals(toBytes(0, false, 1), b(0x10));
		assertArrayEquals(toBytes(0, false, 15), b(0xf0));
		assertArrayEquals(toBytes(0, false, 16), b(0x01, 0x00));
		assertArrayEquals(toBytes(0, false, 79), b(0x04, 0xf0));
		assertArrayEquals(toBytes(0, false, 113), b(0x07, 0x10));
		assertArrayEquals(toBytes(0, false, 26387), b(0x06, 0x71, 0x30));
		assertArrayEquals(toBytes(0, false, 1048575), b(0xff, 0xff, 0xf0));
		assertArrayEquals(toBytes(7, false, 1048575), b(0xff, 0xff, 0xf7));
		assertArrayEquals(toBytes(7, true, 1048575), b(0xff, 0xff, 0xff));
	}

	/**
	 * Tests that the class BlockOption correctly converts the given parameter
	 * to a byte array and back to a BlockOption with the same parameters as
	 * originally.
	 */
	@Test
	public void testCombined() {
		testCombined(0, false, 0);
		testCombined(0, false, 1);
		testCombined(0, false, 15);
		testCombined(0, false, 16);
		testCombined(0, false, 79);
		testCombined(0, false, 113);
		testCombined(0, false, 4096);
		testCombined(0, false, 26387);
		testCombined(0, false, 1048575);
		testCombined(7, false, 1048575);
		testCombined(7, true, 1048575);
	}

	/**
	 * Converts a BlockOption with the specified parameters to a byte array and
	 * back and checks that the result is the same as the original.
	 */
	private static void testCombined(int szx, boolean m, int num) {
		BlockOption block = StandardOptionRegistry.BLOCK1.create(szx, m, num);
		DatagramWriter writer = new DatagramWriter();
		block.writeTo(writer);
		int size = writer.size();
		DatagramReader reader = new DatagramReader(writer.toByteArray());
		BlockOption copy = StandardOptionRegistry.BLOCK1.create(reader, size);
		assertEquals("szx", szx, block.getSzx());
		assertEquals("m", m, block.isM());
		assertEquals("num", num, block.getNum());
		assertEquals("szx", block.getSzx(), copy.getSzx());
		assertEquals("m", block.isM(), copy.isM());
		assertEquals("num", block.getNum(), copy.getNum());
		LOGGER.info("{} == (szx={}, m={}, num={})", Integer.toHexString(block.getIntegerValue()), block.getSzx(),
				block.isM(), block.getNum());
	}

	/**
	 * Helper function that creates a BlockOption with the specified parameters
	 * and serializes them to a byte array.
	 */
	private static byte[] toBytes(int szx, boolean m, int num) {
		BlockOption option = StandardOptionRegistry.BLOCK1.create(szx, m, num);
		byte[] bytes = option.encode();
		LOGGER.info("{} == (szx={}, m={}, num={})", StringUtil.byteArray2Hex(bytes), szx, m, num);
		return bytes;
	}

	/**
	 * Helper function that converts an int array to a byte array.
	 */
	private static byte[] b(int... a) {
		byte[] ret = new byte[a.length];
		for (int i = 0; i < a.length; i++) {
			ret[i] = (byte) a[i];
		}
		return ret;
	}
}
