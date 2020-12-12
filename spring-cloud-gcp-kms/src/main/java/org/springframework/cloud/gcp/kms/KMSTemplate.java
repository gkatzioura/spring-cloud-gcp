/*
 * Copyright 2017-2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.cloud.gcp.kms;

import com.google.cloud.kms.v1.CryptoKeyName;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptRequest;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.common.hash.Hashing;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int64Value;

import org.springframework.cloud.gcp.core.GcpProjectIdProvider;

/**
 * Offers convenience methods for performing common operations on KMS including
 * encrypting and decrypting text.
 *
 * @author Emmanouil Gkatziouras
 */
public class KMSTemplate implements KMSOperations {

	private final KeyManagementServiceClient client;

	private final GcpProjectIdProvider projectIdProvider;

	public KMSTemplate(
			KeyManagementServiceClient keyManagementServiceClient,
			GcpProjectIdProvider projectIdProvider) {
		this.client = keyManagementServiceClient;
		this.projectIdProvider = projectIdProvider;
	}

	public String encrypt(String cryptoKey, String plaintext) {
		CryptoKeyName cryptoKeyName = KMSPropertyUtils.getCryptoKeyName(cryptoKey, projectIdProvider);

		ByteString plaintextByteString = ByteString.copyFromUtf8(plaintext);

		long crc32c = longCrc32c(plaintextByteString);

		EncryptRequest request = EncryptRequest.newBuilder()
				.setName(cryptoKeyName.toString())
				.setPlaintext(plaintextByteString)
				.setPlaintextCrc32C(
						Int64Value.newBuilder().setValue(crc32c).build())
				.build();

		EncryptResponse response = client.encrypt(request);
		assertCrcMatch(response);
		return response.getCiphertext().toStringUtf8();
	}

	public String decrypt(String cryptoKey, String encryptedText) {
		CryptoKeyName cryptoKeyName = KMSPropertyUtils.getCryptoKeyName(cryptoKey, projectIdProvider);
		DecryptResponse decryptResponse = client.decrypt(cryptoKeyName.toString(), ByteString.copyFromUtf8(encryptedText));
		return decryptResponse.getPlaintext().toStringUtf8();
	}

	private long longCrc32c(ByteString plaintextByteString) {
		return Hashing.crc32c().hashBytes(plaintextByteString.toByteArray()).padToLong();
	}

	private void assertCrcMatch(EncryptResponse response) {
		long expected = response.getCiphertextCrc32C().getValue();
		long received = longCrc32c(response.getCiphertext());
		if(expected != received) {
			throw new KMSException("Encryption: response from server corrupted");
		}
	}
}
