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

package com.soklet.example;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.inject.AbstractModule;
import com.google.inject.Injector;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.pyranid.Database;
import com.pyranid.DefaultInstanceProvider;
import com.pyranid.StatementContext;
import com.soklet.SokletConfiguration;
import com.soklet.core.LifecycleInterceptor;
import com.soklet.core.MarshaledResponse;
import com.soklet.core.Request;
import com.soklet.core.ResourceMethod;
import com.soklet.core.Response;
import com.soklet.core.Server;
import com.soklet.core.impl.DefaultResponseMarshaler;
import com.soklet.core.impl.MicrohttpServer;
import com.soklet.core.impl.WhitelistedOriginsCorsAuthorizer;
import org.hsqldb.jdbc.JDBCDataSource;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.annotation.concurrent.ThreadSafe;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static java.util.Objects.requireNonNull;

/**
 * @author <a href="https://www.revetkn.com">Mark Allen</a>
 */
@ThreadSafe
public class AppModule extends AbstractModule {
	@Nonnull
	@Provides
	@Singleton
	public SokletConfiguration provideSokletConfiguration(@Nonnull Injector injector,
																												@Nonnull Configuration configuration,
																												@Nonnull Gson gson) {
		requireNonNull(injector);
		requireNonNull(gson);

		return new SokletConfiguration.Builder(new MicrohttpServer.Builder(configuration.getPort()).host("0.0.0.0").build())
				.lifecycleInterceptor(new LifecycleInterceptor() {
					@Override
					public void didStartRequestHandling(Request request,
																							ResourceMethod resourceMethod) {
						System.out.printf("[%s] Received %s %s\n", request.getId(), request.getHttpMethod(), request.getUri());
					}

					@Override
					public void didFinishRequestHandling(Request request,
																							 ResourceMethod resourceMethod,
																							 MarshaledResponse marshaledResponse,
																							 Duration processingDuration,
																							 List<Throwable> throwables) {
						System.out.printf("[%s] Finished processing %s %s in %sms\n", request.getId(), request.getHttpMethod(), request.getUri(),
								processingDuration.toNanos() / 1000000.0);
					}

					@Override
					public void didStartServer(@Nonnull Server server) {
						System.out.printf("Server started on port %d\n", configuration.getPort());
					}

					@Override
					public void didStopServer(@Nonnull Server server) {
						System.out.println("Server stopped.");
					}
				})
				.responseMarshaler(new DefaultResponseMarshaler() {
					@Nonnull
					@Override
					public MarshaledResponse forHappyPath(@Nonnull Request request,
																								@Nonnull Response response,
																								@Nonnull ResourceMethod resourceMethod) {
						// Use Gson to turn response objects into JSON to go over the wire
						Object bodyObject = response.getBody().orElse(null);
						byte[] body = bodyObject == null ? null : gson.toJson(bodyObject).getBytes(StandardCharsets.UTF_8);

						Map<String, Set<String>> headers = new HashMap<>(response.getHeaders());
						headers.put("Content-Type", Set.of("application/json;charset=UTF-8"));

						return new MarshaledResponse.Builder(response.getStatusCode())
								.headers(headers)
								.cookies(response.getCookies())
								.body(body)
								.build();
					}
				})
				.corsAuthorizer(new WhitelistedOriginsCorsAuthorizer(configuration.getCorsWhitelistedOrigins()))
				// Use Google Guice when Soklet needs to vend instances
				.instanceProvider(injector::getInstance)
				.build();
	}

	@Nonnull
	@Provides
	@Singleton
	public Database provideDatabase(@Nonnull Injector injector) {
		requireNonNull(injector);

		// Example in-memory datasource for HSQLDB
		JDBCDataSource dataSource = new JDBCDataSource();
		dataSource.setUrl("jdbc:hsqldb:mem:example");
		dataSource.setUser("sa");
		dataSource.setPassword("");

		// Use Pyranid to simplify JDBC operations
		return Database.forDataSource(dataSource)
				// Use Google Guice when Pyranid needs to vend instances
				.instanceProvider(new DefaultInstanceProvider() {
					@Override
					@Nonnull
					public <T> T provide(@Nonnull StatementContext<T> statementContext,
															 @Nonnull Class<T> instanceType) {
						return injector.getInstance(instanceType);
					}
				})
				.statementLogger((statementLog) -> {
					// Dump out SQL to the console
					System.out.println(statementLog);
				})
				.build();
	}

	@Nonnull
	@Provides
	@Singleton
	public Gson provideGson() {
		GsonBuilder gsonBuilder = new GsonBuilder()
				.setPrettyPrinting()
				.disableHtmlEscaping()
				// Support `Locale` type for handling locales
				.registerTypeAdapter(Locale.class, new TypeAdapter<Locale>() {
					@Override
					public void write(@Nonnull JsonWriter jsonWriter,
														@Nonnull Locale locale) throws IOException {
						jsonWriter.value(locale.toLanguageTag());
					}

					@Override
					public Locale read(@Nonnull JsonReader jsonReader) throws IOException {
						return Locale.forLanguageTag(jsonReader.nextString());
					}
				})
				// Support `ZoneId` type for handling timezones
				.registerTypeAdapter(ZoneId.class, new TypeAdapter<ZoneId>() {
					@Override
					public void write(@Nonnull JsonWriter jsonWriter,
														@Nonnull ZoneId zoneId) throws IOException {
						jsonWriter.value(zoneId.getId());
					}

					@Override
					@Nullable
					public ZoneId read(@Nonnull JsonReader jsonReader) throws IOException {
						return ZoneId.of(jsonReader.nextString());
					}
				});
		return gsonBuilder.create();
	}
}