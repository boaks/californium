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
package org.eclipse.californium.cloud.s3.forward;

import java.util.ArrayList;
import java.util.List;

/**
 * Http forward providers.
 * <p>
 * List of {@link HttpForwardConfigurationProvider}. Merges all
 * {@link HttpForwardConfiguration} along the list.
 * 
 * @since 4.0
 */
public class HttpForwardConfigurationProviders implements HttpForwardConfigurationProvider {

	/**
	 * List of {@link HttpForwardConfigurationProvider}.
	 */
	private final List<HttpForwardConfigurationProvider> list = new ArrayList<>();

	/**
	 * Creates list of {@link HttpForwardConfigurationProvider}.
	 * 
	 * @param provider primary http-forward configuration provider
	 * @param extraProviders additional http-forward configuration provider
	 */
	public HttpForwardConfigurationProviders(HttpForwardConfigurationProvider provider,
			HttpForwardConfigurationProvider... extraProviders) {
		add(provider);
		if (extraProviders != null) {
			for (HttpForwardConfigurationProvider extraProvider : extraProviders) {
				add(extraProvider);
			}
		}
	}

	/**
	 * Adds provider.
	 * 
	 * @param provider provider. Maybe {@code null}, which is ignored
	 */
	public void add(HttpForwardConfigurationProvider provider) {
		if (provider != null) {
			list.add(provider);
		}
	}

	/**
	 * {@inheritDoc}
	 * 
	 * Gets the http forward configurations for the providers merging the later
	 * configurations into the combined configuration.
	 * <p>
	 * Example:
	 * <ul>
	 * <li>1. provider for device specific configurations</li>
	 * <li>2. provider for default configuration, e.g. only the destination</li>
	 * </ul>
	 * results in the device specific configurations and as default destination
	 * the one from the general configuration.
	 */
	@Override
	public HttpForwardConfiguration getConfiguration(String domain, String name) {
		HttpForwardConfiguration configuration = null;
		for (HttpForwardConfigurationProvider provider : list) {
			configuration = BasicHttpForwardConfiguration.merge(configuration, provider.getConfiguration(domain, name));
		}
		return configuration;
	}

}