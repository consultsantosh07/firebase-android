// Copyright 2021 Google LLC
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package com.google.firebase.appdistribution.impl;

import static com.google.common.truth.Truth.assertThat;
import static com.google.firebase.appdistribution.impl.TestUtils.readTestFile;
import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import androidx.test.core.app.ApplicationProvider;
import com.google.common.collect.Iterators;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.appdistribution.FirebaseAppDistributionException;
import com.google.firebase.appdistribution.FirebaseAppDistributionException.Status;
import com.google.firebase.appdistribution.impl.TesterApiHttpClient.HttpResponse;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import javax.net.ssl.HttpsURLConnection;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.robolectric.RobolectricTestRunner;

@RunWith(RobolectricTestRunner.class)
public class TesterApiHttpClientTest {

  private static final String TEST_API_KEY = "AIzaSyabcdefghijklmnopqrstuvwxyz1234567";
  private static final String TEST_APP_ID_1 = "1:123456789:android:abcdef";
  private static final String TEST_PROJECT_ID = "777777777777";
  private static final String TEST_AUTH_TOKEN = "fad.auth.token";
  private static final String INVALID_RESPONSE = "InvalidResponse";
  private static final String TEST_PATH = "some/url/path";
  private static final String TEST_URL =
      String.format("https://firebaseapptesters.googleapis.com/%s", TEST_PATH);

  private TesterApiHttpClient testerApiHttpClient;
  @Mock private HttpsURLConnection mockHttpsURLConnection;
  @Mock private HttpsUrlConnectionFactory mockHttpsURLConnectionFactory;

  @Before
  public void setup() throws Exception {
    MockitoAnnotations.initMocks(this);
    FirebaseApp.clearInstancesForTest();

    when(mockHttpsURLConnectionFactory.openConnection(TEST_URL)).thenReturn(mockHttpsURLConnection);

    FirebaseApp firebaseApp =
        FirebaseApp.initializeApp(
            ApplicationProvider.getApplicationContext(),
            new FirebaseOptions.Builder()
                .setApplicationId(TEST_APP_ID_1)
                .setProjectId(TEST_PROJECT_ID)
                .setApiKey(TEST_API_KEY)
                .build());

    testerApiHttpClient = new TesterApiHttpClient(firebaseApp, mockHttpsURLConnectionFactory);
  }

  @Test
  public void makeGetRequest_whenResponseSuccessful_returnsJsonResponse() throws Exception {
    String responseJson = readTestFile("testSimpleResponse.json");
    InputStream responseInputStream =
        new ByteArrayInputStream(responseJson.getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);
    when(mockHttpsURLConnection.getInputStream()).thenReturn(responseInputStream);

    HttpResponse response = testerApiHttpClient.makeGetRequest(TEST_PATH, TEST_AUTH_TOKEN);

    assertThat(response.code()).isEqualTo(200);
    assertThat(Iterators.getOnlyElement(response.body().keys())).isEqualTo("fieldName");
    assertThat(response.body().getString("fieldName")).isEqualTo("fieldValue");
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void makeGetRequest_whenResponseNotSuccessful_stillReturnsJsonResponse() throws Exception {
    InputStream responseInputStream =
        new ByteArrayInputStream(INVALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(401);
    when(mockHttpsURLConnection.getInputStream()).thenReturn(responseInputStream);

    HttpResponse response = testerApiHttpClient.makeGetRequest(TEST_PATH, TEST_AUTH_TOKEN);

    assertThat(response.code()).isEqualTo(401);
    assertThat(Iterators.size(response.body().keys())).isEqualTo(0);
    verify(mockHttpsURLConnection).disconnect();
  }

  @Test
  public void makeGetRequest_whenConnectionFails_throwsError() throws Exception {
    IOException caughtException = new IOException("error");
    when(mockHttpsURLConnectionFactory.openConnection(TEST_URL)).thenThrow(caughtException);

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () -> testerApiHttpClient.makeGetRequest(TEST_PATH, TEST_AUTH_TOKEN));

    assertThat(e.getErrorCode()).isEqualTo(Status.NETWORK_FAILURE);
    assertThat(e.getMessage()).isEqualTo(ErrorMessages.NETWORK_ERROR);
  }

  @Test
  public void makeGetRequest_whenInvalidJson_throwsError() throws Exception {
    InputStream response =
        new ByteArrayInputStream(INVALID_RESPONSE.getBytes(StandardCharsets.UTF_8));
    when(mockHttpsURLConnection.getInputStream()).thenReturn(response);
    when(mockHttpsURLConnection.getResponseCode()).thenReturn(200);

    FirebaseAppDistributionException e =
        assertThrows(
            FirebaseAppDistributionException.class,
            () -> testerApiHttpClient.makeGetRequest(TEST_PATH, TEST_AUTH_TOKEN));

    assertThat(e.getErrorCode()).isEqualTo(Status.UNKNOWN);
    assertThat(e.getMessage()).isEqualTo(ErrorMessages.JSON_PARSING_ERROR);
    verify(mockHttpsURLConnection).disconnect();
  }
}
