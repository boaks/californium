/********************************************************************************
 * Copyright (c) 2024 Contributors to the Eclipse Foundation
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

import org.eclipse.californium.core.coap.CoAP.Type;
import org.eclipse.californium.core.coap.Option;
import org.eclipse.californium.core.coap.OptionSet;
import org.eclipse.californium.core.coap.Request;
import org.eclipse.californium.core.coap.Response;

/**
 * CoAP custom option for transmission counter.
 * 
 * Inspired by <a href=
 * "https://www.ietf.org/archive/id/draft-ietf-core-fasor-02.html#section-4.4"
 * target ="_blank"> Fast-Slow Retransmission Timeout and Congestion Control
 * Algorithm for CoAP, 4.4. Retransmission Count Option</a>
 * 
 * @since 3.13
 */
public class TransmissionCountOption extends Option {

	/**
	 * Number of custom option.
	 */
	public static final int COAP_OPTION_RETRANSMISSION_COUNTER = 0xfdfc;

	public static final IntegerOptionDefinition DEFINITION = new IntegerOptionDefinition(
			COAP_OPTION_RETRANSMISSION_COUNTER, "Retransmission Counter", true, 1, 1) {

		@Override
		public Option create(byte[] value) {
			return new TransmissionCountOption(value);
		}

	};

	/**
	 * Create retransmission counter option.
	 * 
	 * @param retransmission number of retransmission
	 */
	public TransmissionCountOption(int retransmission) {
		super(DEFINITION, retransmission);
	}

	/**
	 * Create retransmission counter option.
	 * 
	 * @param value number of retransmission
	 */
	public TransmissionCountOption(byte[] value) {
		super(DEFINITION, value);
	}

	/**
	 * Increment retransmission counter option of request.
	 * 
	 * @param request request to increment retransmission counter
	 * @return {@code true}, if option value was incremented, {@code false}, if
	 *         option isn't available.
	 * @throws NullPointerException if request is {@code null}
	 */
	public static boolean increment(Request request) {
		if (request == null) {
			throw new NullPointerException("request must not be null!");
		}
		OptionSet options = request.getOptions();
		Option option = options.getOtherOption(DEFINITION);
		if (option != null) {
			int counter = option.getIntegerValue();
			options.clearOtherOption(option);
			options.addOtherOption(DEFINITION.create(counter + 1));
			// trigger new serialization with incremented counter
			request.setBytes(null);
			return true;
		}
		return false;
	}

	/**
	 * Transcribe retransmission counter option from request to response.
	 * 
	 * @param request request to check for retransmission counter
	 * @param response response to add retransmission counter
	 * @return {@code true}, if option value was transcribed, {@code false},
	 *         otherwise.
	 * @throws NullPointerException if one of the arguments is {@code null}
	 */
	public static boolean transcribe(Request request, Response response) {
		if (request == null) {
			throw new NullPointerException("request must not be null!");
		}
		if (response == null) {
			throw new NullPointerException("response must not be null!");
		}
		if (response.getType() != Type.CON) {
			OptionSet options = request.getOptions();
			Option option = options.getOtherOption(DEFINITION);
			if (option != null) {
				options = response.getOptions();
				options.clearOtherOption(DEFINITION);
				options.addOtherOption(option);
				return true;
			}
		}
		return false;
	}
}
