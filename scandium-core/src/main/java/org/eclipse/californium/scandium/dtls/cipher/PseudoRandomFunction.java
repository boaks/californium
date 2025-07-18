/*******************************************************************************
 * Copyright (c) 2016 Bosch Software Innovations GmbH and others.
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
 *    Bosch Software Innovations GmbH - refactor existing PRF code from Handshaker
 *                                      into separate class
 ******************************************************************************/
package org.eclipse.californium.scandium.dtls.cipher;

import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.ShortBufferException;

import org.eclipse.californium.elements.util.Bytes;
import org.eclipse.californium.elements.util.DatagramWriter;
import org.eclipse.californium.scandium.util.SecretUtil;

/**
 * The Pseudo Random Function as defined in TLS 1.2.
 * 
 * @see <a href="https://tools.ietf.org/html/rfc5246#section-5" target=
 *      "_blank">RFC 5246</a>
 */
public final class PseudoRandomFunction {

	private PseudoRandomFunction() {
	}

	public enum Label {

		// The master secret is always 48 bytes long, see
		// http://tools.ietf.org/html/rfc5246#section-8.1
		MASTER_SECRET_LABEL("master secret", 48),
		// The most key material required is 128 bytes, see
		// http://tools.ietf.org/html/rfc5246#section-6.3
		// (some cipher suites, not mentioned in rfc5246 requires more!)
		KEY_EXPANSION_LABEL("key expansion", 128),
		// The verify data is always 12 bytes long, see
		// http://tools.ietf.org/html/rfc5246#section-7.4.9
		CLIENT_FINISHED_LABEL("client finished", 12),
		// The verify data is always 12 bytes long, see
		// http://tools.ietf.org/html/rfc5246#section-7.4.9
		SERVER_FINISHED_LABEL("server finished", 12),
		// The extended master secret is always 48 bytes long, see
		// http://tools.ietf.org/html/rfc7621#section-4
		EXTENDED_MASTER_SECRET_LABEL("extended master secret", 48);

		private final String value;
		private final byte[] bytesValue;
		private final int length;

		private Label(String value, int length) {
			this.value = value;
			this.bytesValue = value.getBytes(StandardCharsets.UTF_8);
			this.length = length;
		}

		public String value() {
			return value;
		}

		private byte[] getBytes() {
			return bytesValue;
		}

		public int length() {
			return length;
		}
	}

	private static byte[] EXPORTER = "EXPORTER".getBytes(StandardCharsets.UTF_8);
	private static byte[] EXPERIMENTAL = "EXPERIMENTAL".getBytes(StandardCharsets.UTF_8);

	/**
	 * Check, if array starts with other array.
	 * 
	 * @param value value to check
	 * @param start header to check
	 * @return {@code true}, if value starts with header, {@code false}
	 *         otherwise.
	 * @since 3.10
	 */
	private static boolean startsWith(byte[] value, byte[] start) {
		if (value.length < start.length) {
			return false;
		}
		for (int index = 0; index < start.length; ++index) {
			if (value[index] != start[index]) {
				return false;
			}
		}
		return true;
	}

	/**
	 * Check, if the provided label is supported for key material export.
	 * 
	 * @param label label to check
	 * @return {@code true}, if allowed, {@code false}, if not.
	 * @since 3.10
	 */
	public static boolean isExportLabel(byte[] label) {
		if (startsWith(label, EXPORTER)) {
			return true;
		}
		if (startsWith(label, EXPERIMENTAL)) {
			return true;
		}
		return false;
	}

	/**
	 * Does the pseudo random function as defined in
	 * <a href="https://tools.ietf.org/html/rfc5246#section-5" target=
	 * "_blank">RFC 5246</a>.
	 * 
	 * @param hmac MAC algorithm. e.g. HmacSHA256
	 * @param secret the secret to use for the secure hash function
	 * @param label the label to use for creating the original data.
	 * @param seed the seed to use for creating the original data
	 * @param length the length of data to create
	 * @return the expanded data
	 */
	static byte[] doPRF(Mac hmac, SecretKey secret, byte[] label, byte[] seed, int length) {
		try {
			hmac.init(secret);
			byte[] prf = doExpansion(hmac, label, seed, length);
			hmac.reset();
			return prf;
		} catch (InvalidKeyException e) {
			// according to http://www.ietf.org/rfc/rfc2104 (HMAC) section 3
			// keys can be of arbitrary length
			throw new IllegalArgumentException("Cannot run Pseudo Random Function with invalid key", e);
		}
	}

	/**
	 * Calculate the pseudo random function for exporter as defined in
	 * <a href="https://tools.ietf.org/html/rfc5246#section-5" target=
	 * "_blank">RFC 5246</a>.
	 * 
	 * @param hmac MAC algorithm. e.g. HmacSHA256
	 * @param secret the secret to use for the secure hash function
	 * @param label the label to use for creating the original data.
	 * @param seed the seed to use for creating the original data
	 * @param length the length of data to create
	 * @return the expanded data
	 * @throws IllegalArgumentException if label is not allowed for exporter
	 * @see <a href="https://tools.ietf.org/html/rfc5705" target="_blank">RFC
	 *      5705</a>
	 * @since 3.10
	 */
	public static final byte[] doExporterPRF(Mac hmac, SecretKey secret, byte[] label, byte[] seed, int length) {
		if (!isExportLabel(label)) {
			throw new IllegalArgumentException("label must be valid for export!");
		}
		return doPRF(hmac, secret, label, seed, length);
	}

	/**
	 * Does the pseudo random function as defined in
	 * <a href="https://tools.ietf.org/html/rfc5246#section-5" target=
	 * "_blank">RFC 5246</a>.
	 * 
	 * @param hmac MAC algorithm. e.g. HmacSHA256
	 * @param secret the secret to use for the secure hash function
	 * @param label the label to use for creating the original data. Uses the
	 *            length from the label.
	 * @param seed the seed to use for creating the original data
	 * @return the expanded data
	 */
	public static final byte[] doPRF(Mac hmac, SecretKey secret, Label label, byte[] seed) {
		return doPRF(hmac, secret, label.getBytes(), seed, label.length());
	}

	/**
	 * Does the pseudo random function as defined in
	 * <a href="https://tools.ietf.org/html/rfc5246#section-5" target=
	 * "_blank">RFC 5246</a>.
	 * 
	 * @param hmac MAC algorithm. e.g. HmacSHA256
	 * @param secret the secret to use for the secure hash function
	 * @param label the label to use for creating the original data
	 * @param seed the seed to use for creating the original data
	 * @param length the length of data to create
	 * @return the expanded data
	 */
	public static final byte[] doPRF(Mac hmac, SecretKey secret, Label label, byte[] seed, int length) {
		return doPRF(hmac, secret, label.getBytes(), seed, length);
	}

	/**
	 * Performs the secret expansion as described in
	 * <a href="https://tools.ietf.org/html/rfc5246#section-5" target=
	 * "_blank">RFC 5246</a>.
	 * 
	 * @param hmac the cryptographic hash function to use for expansion.
	 * @param label the label to use for creating the original data
	 * @param seed the seed to use for creating the original data
	 * @param length the number of bytes to expand the data to.
	 * @return the expanded data.
	 */
	static final byte[] doExpansion(Mac hmac, byte[] label, byte[] seed, int length) {
		/*
		 * RFC 5246, chapter 5, page 15
		 * 
		 * P_hash(secret, seed) = 
		 *    HMAC_hash(secret, A(1) + seed) +
		 *    HMAC_hash(secret, A(2) + seed) + 
		 *    HMAC_hash(secret, A(3) + seed) + ...
		 * where + indicates concatenation.
		 *  
		 * A() is defined as: 
		 *    A(0) = seed, 
		 *    A(i) = HMAC_hash(secret, A(i-1))
		 */

		int offset = 0;
		final int macLength = hmac.getMacLength();
		final byte[] aAndSeed = new byte[macLength + label.length + seed.length];
		final byte[] expansion = new byte[length];
		try {
			// copy appended seed to buffer end
			System.arraycopy(label, 0, aAndSeed, macLength, label.length);
			System.arraycopy(seed, 0, aAndSeed, macLength + label.length, seed.length);
			// calculate A(n) from A(0)
			hmac.update(label);
			hmac.update(seed);
			while (true) {
				// write result to "A(n) + seed"
				hmac.doFinal(aAndSeed, 0);
				// calculate HMAC_hash from "A(n) + seed"
				hmac.update(aAndSeed);
				final int nextOffset = offset + macLength;
				if (nextOffset > length) {
					// too large for expansion!
					// write HMAC_hash result temporary to "A(n) + seed"
					hmac.doFinal(aAndSeed, 0);
					// write head of result from temporary "A(n) + seed" to
					// expansion
					System.arraycopy(aAndSeed, 0, expansion, offset, length - offset);
					break;
				} else {
					// write HMAC_hash result to expansion
					hmac.doFinal(expansion, offset);
					if (nextOffset == length) {
						break;
					}
				}
				offset = nextOffset;
				// calculate A(n+1) from "A(n) + seed" head ("A(n)")
				hmac.update(aAndSeed, 0, macLength);
			}
		} catch (ShortBufferException e) {
			e.printStackTrace();
		}
		Bytes.clear(aAndSeed);
		return expansion;
	}

	/**
	 * Generate (extended) master secret.
	 * 
	 * @param hmac MAC algorithm. e.g. HmacSHA256
	 * @param premasterSecret the secret to use for the secure hash function
	 * @param seed the seed to use for creating the master secret
	 * @param extended {@code true}, use
	 *            {@link Label#EXTENDED_MASTER_SECRET_LABEL}, {@code false}, use
	 *            {@link Label#MASTER_SECRET_LABEL}
	 * @return the (extended) master secret
	 * @since 3.0 (added parameter extended)
	 */
	public static SecretKey generateMasterSecret(Mac hmac, SecretKey premasterSecret, byte[] seed, boolean extended) {
		byte[] secret = doPRF(hmac, premasterSecret,
				extended ? Label.EXTENDED_MASTER_SECRET_LABEL : Label.MASTER_SECRET_LABEL, seed);
		SecretKey masterSecret = SecretUtil.create(secret, "MAC");
		Bytes.clear(secret);
		return masterSecret;
	}

	/**
	 * The premaster secret is formed as follows: if the PSK is N octets long,
	 * concatenate a uint16 with the value N, N zero octets, a second uint16
	 * with the value N, and the PSK itself.
	 * 
	 * @param otherSecret other secret derived from the EC Diffie-Hellman
	 *            exchange (ECDHE_PSK). Or {@code null} for plain PSK exchange.
	 * @param pskSecret PSK secret.
	 * @return byte array with generated premaster secret.
	 * @see <a href="https://tools.ietf.org/html/rfc4279#section-2" target=
	 *      "_blank">RFC 4279</a>
	 */
	public static SecretKey generatePremasterSecretFromPSK(SecretKey otherSecret, SecretKey pskSecret) {
		/*
		 * What we are building is the following with length fields in between:
		 * struct { opaque other_secret<0..2^16-1>; opaque psk<0..2^16-1>; };
		 */
		byte[] pskBytes = pskSecret.getEncoded();
		int pskLength = pskBytes.length;
		byte[] otherBytes = otherSecret != null ? otherSecret.getEncoded() : new byte[pskLength];
		DatagramWriter writer = new DatagramWriter(otherBytes.length + pskLength + 4, true);
		writer.writeVarBytes(otherBytes, 16);
		writer.writeVarBytes(pskBytes, 16);
		byte[] secret = writer.toByteArray();
		writer.close();
		SecretKey premaster = SecretUtil.create(secret, "MAC");
		Bytes.clear(pskBytes);
		Bytes.clear(otherBytes);
		Bytes.clear(secret);
		return premaster;
	}
}
