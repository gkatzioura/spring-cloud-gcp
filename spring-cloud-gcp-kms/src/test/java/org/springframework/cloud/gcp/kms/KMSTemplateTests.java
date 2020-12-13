package org.springframework.cloud.gcp.kms;

import java.util.Base64;

import com.google.api.gax.rpc.InvalidArgumentException;
import com.google.api.gax.rpc.NotFoundException;
import com.google.api.gax.rpc.PermissionDeniedException;
import com.google.cloud.kms.v1.DecryptRequest;
import com.google.cloud.kms.v1.DecryptResponse;
import com.google.cloud.kms.v1.EncryptRequest;
import com.google.cloud.kms.v1.EncryptResponse;
import com.google.cloud.kms.v1.KeyManagementServiceClient;
import com.google.protobuf.ByteString;
import com.google.protobuf.Int64Value;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class KMSTemplateTests {

	private KeyManagementServiceClient client;

	private KMSTemplate kmsTemplate;

	@Before
	public void setupMocks() {
		this.client = mock(KeyManagementServiceClient.class);
		this.kmsTemplate = new KMSTemplate(this.client, () -> "my-project");
	}

	@Test
	public void testEncryptDecrypt() {
		EncryptResponse encryptResponse = createEncryptResponse();
		DecryptResponse decryptResponse = createDecryptResponse();

		when(this.client.encrypt(any(EncryptRequest.class))).thenReturn(encryptResponse);
		when(this.client.decrypt(any(DecryptRequest.class))).thenReturn(decryptResponse);

		String cryptoKeyNameStr = "kms://test-project/europe-west2/key-ring-id/key-id";

		String encryptedText = kmsTemplate.encrypt(cryptoKeyNameStr, "1234");
		String decryptedText = kmsTemplate.decrypt(cryptoKeyNameStr, encryptedText);

		Assert.assertEquals("1234", decryptedText);
	}

	@Test(expected = org.springframework.cloud.gcp.kms.KMSException.class)
	public void testEncryptCorrupt() {
		EncryptResponse encryptResponse = EncryptResponse.newBuilder()
				.setCiphertext(ByteString.copyFromUtf8("invalid"))
				.setCiphertextCrc32C(Int64Value.newBuilder().setValue(0l).build())
				.build();

		when(this.client.encrypt(any(EncryptRequest.class))).thenReturn(encryptResponse);

		String cryptoKeyNameStr = "kms://test-project/europe-west2/key-ring-id/key-id";
		kmsTemplate.encrypt(cryptoKeyNameStr, "1234");
	}

	@Test(expected = org.springframework.cloud.gcp.kms.KMSException.class)
	public void testDecryptCorrupt() {
		DecryptResponse decryptResponse = DecryptResponse.newBuilder()
				.setPlaintext(ByteString.copyFromUtf8("1234"))
				.setPlaintextCrc32C(Int64Value.newBuilder().setValue(0l).build())
				.build();

		when(this.client.decrypt(any(DecryptRequest.class))).thenReturn(decryptResponse);

		String cryptoKeyNameStr = "kms://test-project/europe-west2/key-ring-id/key-id";
		kmsTemplate.decrypt(cryptoKeyNameStr, "ZW5jcnlwdGVkLWJ5dGVzCg==");
	}

	@Test(expected = com.google.api.gax.rpc.InvalidArgumentException.class)
	public void testEncryptDecryptMissMatch() {
		EncryptResponse encryptResponse = createEncryptResponse();

		when(this.client.encrypt(any(EncryptRequest.class))).thenReturn(encryptResponse);
		when(this.client.decrypt(any(DecryptRequest.class))).thenThrow(InvalidArgumentException.class);

		String cryptoKeyNameStr = "kms://test-project/europe-west2/key-ring-id/key-id";

		String encryptedText = kmsTemplate.encrypt(cryptoKeyNameStr, "1234");
		kmsTemplate.decrypt(cryptoKeyNameStr, encryptedText);
	}

	@Test(expected = com.google.api.gax.rpc.PermissionDeniedException.class)
	public void testEncryptPermissionDenied() {
		when(this.client.encrypt(any(EncryptRequest.class))).thenThrow(PermissionDeniedException.class);

		String cryptoKeyNameStr = "kms://test-project/europe-west2/no-access/key-id";

		String encryptedText = kmsTemplate.encrypt(cryptoKeyNameStr, "1234");
		kmsTemplate.decrypt(cryptoKeyNameStr, encryptedText);
	}

	@Test(expected = com.google.api.gax.rpc.NotFoundException.class)
	public void testEncryptNotFound() {
		when(this.client.encrypt(any(EncryptRequest.class))).thenThrow(NotFoundException.class);

		String cryptoKeyNameStr = "kms://test-project/europe-west2/key-ring-id/not-found";

		String encryptedText = kmsTemplate.encrypt(cryptoKeyNameStr, "1234");
		kmsTemplate.decrypt(cryptoKeyNameStr, encryptedText);
	}

	private DecryptResponse createDecryptResponse() {
		return DecryptResponse.newBuilder()
					.setPlaintext(ByteString.copyFromUtf8("1234"))
					.setPlaintextCrc32C(Int64Value.newBuilder().setValue(4131058926l).build())
					.build();
	}

	private EncryptResponse createEncryptResponse() {
		String kmsEncryptedText = "CiQAIVnQi1mE20Me07FF9myX9IUDmx1HmvUre6/VrVbTcJYY9L4SLQAe63KkBSKEz+TmOTXyMxeS/M1DtaPk3rVIBWyshVeZ+FhUHMoOAu6Fx6Shkw==";
		byte[] encryptedBytes = Base64.getDecoder().decode(kmsEncryptedText.getBytes());

		return EncryptResponse.newBuilder()
				.setCiphertext(ByteString.copyFrom(encryptedBytes))
				.setCiphertextCrc32C(Int64Value.newBuilder().setValue(1171937405l).build())
				.build();
	}

}