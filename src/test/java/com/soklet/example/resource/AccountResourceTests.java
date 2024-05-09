/*
 * Copyright 2022-2023 Revetware LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.soklet.example.resource;

import com.google.gson.Gson;
import com.soklet.Soklet;
import com.soklet.SokletConfiguration;
import com.soklet.annotation.Resource;
import com.soklet.core.HttpMethod;
import com.soklet.core.MarshaledResponse;
import com.soklet.core.Request;
import com.soklet.example.App;
import com.soklet.example.Configuration;
import com.soklet.example.resource.AccountResource.AccountAuthenticateReponse;
import org.junit.Assert;
import org.junit.Test;

import javax.annotation.concurrent.ThreadSafe;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@Resource
@ThreadSafe
public class AccountResourceTests {
	@Test
	public void testAuthenticate() {
		App app = new App(new Configuration());
		Gson gson = app.getInjector().getInstance(Gson.class);
		SokletConfiguration config = app.getInjector().getInstance(SokletConfiguration.class);

		Soklet.runSimulator(config, (simulator -> {
			String requestBodyJson = gson.toJson(Map.of(
					"emailAddress", "admin@soklet.com",
					"password", "fake-password"
			));

			Request request = Request.with(HttpMethod.POST, "/employees/authenticate")
					.body(requestBodyJson.getBytes(StandardCharsets.UTF_8))
					.build();

			MarshaledResponse marshaledResponse = simulator.performRequest(request);

			String responseBody = new String(marshaledResponse.getBody().get(), StandardCharsets.UTF_8);
			AccountAuthenticateReponse response = gson.fromJson(responseBody, AccountAuthenticateReponse.class);

			Assert.assertEquals("Bad status code", 200, (long) marshaledResponse.getStatusCode());
			Assert.assertEquals("Email doesn't match", "admin@soklet.com", response.employee().getEmailAddress().get());

			requestBodyJson = gson.toJson(Map.of(
					"emailAddress", "fake@soklet.com",
					"password", "fake-password"
			));

			request = Request.with(HttpMethod.POST, "/employees/authenticate")
					.body(requestBodyJson.getBytes(StandardCharsets.UTF_8))
					.build();

			marshaledResponse = simulator.performRequest(request);

			Assert.assertEquals("Bad status code", 401, (long) marshaledResponse.getStatusCode());
		}));
	}
}