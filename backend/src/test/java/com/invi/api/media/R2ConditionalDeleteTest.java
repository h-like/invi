package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.invi.api.common.StorageUnavailableException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import software.amazon.awssdk.awscore.exception.AwsServiceException;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.http.SdkHttpResponse;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
class R2ConditionalDeleteTest {
    private static final String BUCKET = "invi-media";
    private static final String MANAGED_KEY = "invitations/11111111-1111-4111-8111-111111111111/"
            + "22222222-2222-4222-8222-222222222222.webp";
    private static final String STALE_ETAG = "\"genuine-original-etag\"";
    private static final String ETAG = "\"replacement-current-etag\"";
    private static final String PROBE_PREFIX = "invi-cleanup-probes/";
    private static final String UUID_PATTERN = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    @Mock private S3Client client;
    private final R2ConditionalDelete helper = new R2ConditionalDelete();

    @Test
    void managedDeleteSendsExactlyTheObservedIfMatchHeader() {
        helper.deleteIfUnchanged(client, BUCKET, MANAGED_KEY, ETAG);
        ArgumentCaptor<DeleteObjectRequest> deletes = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(deletes.capture());
        assertThat(deletes.getValue().bucket()).isEqualTo(BUCKET);
        assertThat(deletes.getValue().key()).isEqualTo(MANAGED_KEY);
        assertConditional(deletes.getValue(), ETAG);
        verifyNoMoreInteractions(client);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t", "\r\n"})
    void managedDeleteRejectsMissingOrBlankEtagWithoutAnyS3Call(String etag) {
        assertThatThrownBy(() -> helper.deleteIfUnchanged(client, BUCKET, MANAGED_KEY, etag))
                .isInstanceOf(StorageUnavailableException.class);
        verifyNoInteractions(client);
    }

    @Test
    void realManagedDelete412PropagatesWithoutUnconditionalFallback() {
        AwsServiceException changed = failure(412);
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenThrow(changed);
        assertThatThrownBy(() -> helper.deleteIfUnchanged(client, BUCKET, MANAGED_KEY, ETAG))
                .isSameAs(changed);
        ArgumentCaptor<DeleteObjectRequest> deletes = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client).deleteObject(deletes.capture());
        assertThat(deletes.getValue().key()).isEqualTo(MANAGED_KEY);
        assertConditional(deletes.getValue(), ETAG);
        verifyNoMoreInteractions(client);
    }

    @Test
    void capabilityRequiresRealStaleTag412UnchangedCurrentTagAndFinalHead404() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG), head(ETAG)).thenThrow(failure(404));
        rejectStaleTag();
        assertThatCode(() -> helper.verifyCapability(client, BUCKET)).doesNotThrowAnyException();
        InOrder order = inOrder(client);
        order.verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        order.verify(client).headObject(any(HeadObjectRequest.class));
        order.verify(client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
        order.verify(client).headObject(any(HeadObjectRequest.class));
        order.verify(client).deleteObject(any(DeleteObjectRequest.class));
        order.verify(client).headObject(any(HeadObjectRequest.class));
        order.verify(client).deleteObject(any(DeleteObjectRequest.class));
        order.verify(client).headObject(any(HeadObjectRequest.class));
        order.verify(client).deleteObject(any(DeleteObjectRequest.class));
        order.verifyNoMoreInteractions();
        assertProbeTraffic(2, 4, 3);
    }

    @Test
    void ignoredIfMatchReturning200RefusesBeforeAnyManagedDelete() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(STALE_ETAG), head(ETAG));
        when(client.deleteObject(any(DeleteObjectRequest.class))).thenReturn(okDelete());
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 2, 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 404, 500, 503})
    void staleTagErrorOtherThan412CannotEstablishCapability(int status) throws Exception {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(STALE_ETAG), head(ETAG));
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(failure(status)).thenReturn(okDelete());
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 2, 2);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 503})
    void initialHeadFailureRefusesBeforeConditionalOrManagedDeletion(int status) throws Exception {
        when(client.headObject(any(HeadObjectRequest.class))).thenThrow(failure(status));
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(1, 1, 1);
    }

    @Test
    void verificationHeadFailureRefusesBeforeCorrectTagOrManagedDeletion() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG))
                .thenThrow(SdkClientException.create("private HEAD endpoint"));
        rejectStaleTag();
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 3, 2);
    }

    @Test
    void missingInitialHeadResponseRefusesBeforeConditionalOrManagedDeletion() throws Exception {
        // The default null response models incomplete SDK metadata.
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(1, 1, 1);
    }

    @Test
    void missingVerificationHeadResponseRefusesBeforeCorrectTagOrManagedDeletion() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG)).thenReturn(null);
        rejectStaleTag();
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 3, 2);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void missingOrBlankInitialHeadEtagRefusesBeforeConditionalOrManagedDeletion(String etag) throws Exception {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(etag));
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(1, 1, 1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void missingOrBlankVerificationHeadEtagRefusesBeforeCorrectTagOrManagedDeletion(String etag) throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG), head(etag));
        rejectStaleTag();
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 3, 2);
    }

    @Test
    void changedVerificationHeadEtagRefusesBeforeCorrectTagOrManagedDeletion() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG), head("\"unexpected-replacement\""));
        rejectStaleTag();
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 3, 2);
    }

    @Test
    void failedInitialPutRefusesAndOnlyAttemptsIsolatedProbeCleanup() throws Exception {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("private PUT endpoint"));
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(1, 0, 1);
    }

    @Test
    void correctTagDeleteFailureAlsoRefusesBeforeAnyManagedDelete() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG), head(ETAG));
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(failure(412)).thenThrow(failure(503)).thenReturn(okDelete());
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 3, 3);
    }

    @Test
    void isolatedFinallyCleanupFailureIsSwallowedAfterSuccessfulCapabilityCheck() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG), head(ETAG)).thenThrow(failure(404));
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(failure(412)).thenReturn(okDelete())
                .thenThrow(SdkClientException.create("private cleanup endpoint"));
        assertThatCode(() -> helper.verifyCapability(client, BUCKET)).doesNotThrowAnyException();
        assertProbeTraffic(2, 4, 3);
    }

    @Test
    void cleanupFailureCannotTurnIgnoredIfMatchIntoCapabilitySuccess() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(STALE_ETAG), head(ETAG));
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(okDelete()).thenThrow(failure(503));
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 2, 2);
    }

    @Test
    void cleanupFailureCannotReplaceOrExposePrivatePutFailure() throws Exception {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenThrow(SdkClientException.create("private PUT endpoint"));
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(SdkClientException.create("private cleanup endpoint"));
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(1, 0, 1);
    }

    @Test
    void sameEtagAfterOverwriteRefusesBeforeAnyConditionalOrManagedDelete() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(STALE_ETAG));
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 2, 1);
    }

    @Test
    void failedReplacementPutRefusesBeforeAnyConditionalOrManagedDelete() throws Exception {
        when(client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(software.amazon.awssdk.services.s3.model.PutObjectResponse.builder().build())
                .thenThrow(SdkClientException.create("private replacement PUT endpoint"));
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(STALE_ETAG));
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 1, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {404, 503})
    void overwrittenObjectHeadFailureRefusesBeforeAnyConditionalOrManagedDelete(int status) throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG)).thenThrow(failure(status));
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 2, 1);
    }

    @Test
    void missingOverwrittenObjectHeadResponseRefusesBeforeAnyConditionalOrManagedDelete() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(STALE_ETAG)).thenReturn(null);
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 2, 1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t\n"})
    void missingOrBlankOverwrittenObjectEtagRefusesBeforeAnyConditionalOrManagedDelete(String etag)
            throws Exception {
        when(client.headObject(any(HeadObjectRequest.class))).thenReturn(head(STALE_ETAG), head(etag));
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 2, 1);
    }

    @Test
    void successfulCorrectTagDeleteThatIsANoopAndLeavesHead200RefusesCapability() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG), head(ETAG), head(ETAG));
        rejectStaleTag();
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 4, 3);
    }

    @ParameterizedTest
    @ValueSource(ints = {403, 412, 500, 503})
    void finalHeadErrorOtherThan404RefusesCapability(int status) throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG), head(ETAG)).thenThrow(failure(status));
        rejectStaleTag();
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 4, 3);
    }

    @Test
    void finalHeadSdkFailureDoesNotProveAbsence() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG), head(ETAG))
                .thenThrow(SdkClientException.create("private final HEAD endpoint"));
        rejectStaleTag();
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 4, 3);
    }

    @Test
    void missingFinalHeadResponseDoesNotProveAbsence() throws Exception {
        when(client.headObject(any(HeadObjectRequest.class)))
                .thenReturn(head(STALE_ETAG), head(ETAG), head(ETAG)).thenReturn(null);
        rejectStaleTag();
        assertCapabilityRefusesManagedDelete();
        assertProbeTraffic(2, 4, 3);
    }

    private void assertCapabilityRefusesManagedDelete() {
        assertThatThrownBy(() -> {
            helper.verifyCapability(client, BUCKET);
            // Model the caller's safety gate: no managed object may be touched on probe failure.
            helper.deleteIfUnchanged(client, BUCKET, MANAGED_KEY, ETAG);
        }).isInstanceOf(StorageUnavailableException.class)
                .hasNoCause()
                .hasMessageNotContaining("private")
                .hasMessageNotContaining(MANAGED_KEY)
                .hasMessageNotContaining(BUCKET);
    }

    private void rejectStaleTag() {
        when(client.deleteObject(any(DeleteObjectRequest.class)))
                .thenThrow(failure(412)).thenReturn(okDelete());
    }

    private void assertProbeTraffic(int putCount, int headCount, int deleteCount) throws Exception {
        ArgumentCaptor<PutObjectRequest> puts = ArgumentCaptor.forClass(PutObjectRequest.class);
        ArgumentCaptor<RequestBody> bodies = ArgumentCaptor.forClass(RequestBody.class);
        verify(client, times(putCount)).putObject(puts.capture(), bodies.capture());
        String key = puts.getAllValues().get(0).key();
        assertThat(key).matches(PROBE_PREFIX + UUID_PATTERN);
        assertThat(UUID.fromString(key.substring(PROBE_PREFIX.length())).toString())
                .isEqualTo(key.substring(PROBE_PREFIX.length()));
        assertThat(key).doesNotStartWith("invitations/");
        assertThat(puts.getAllValues()).allSatisfy(put -> {
            assertThat(put.bucket()).isEqualTo(BUCKET);
            assertThat(put.key()).isEqualTo(key);
            assertThat(put.contentType()).isEqualTo("application/octet-stream");
            // Neither version exposes a public ACL, invitation payload, URL, or user metadata.
            assertThat(put.acl()).isNull();
            assertThat(put.metadata()).isEmpty();
            assertThat(put.websiteRedirectLocation()).isNull();
        });
        for (int index = 0; index < bodies.getAllValues().size(); index++) {
            String expected = index == 0 ? "invi cleanup capability check"
                    : "invi cleanup capability check replacement";
            try (InputStream bytes = bodies.getAllValues().get(index).contentStreamProvider().newStream()) {
                assertThat(bytes.readAllBytes()).isEqualTo(expected.getBytes(StandardCharsets.UTF_8));
            }
        }
        if (putCount == 2) {
            try (InputStream original = bodies.getAllValues().get(0).contentStreamProvider().newStream();
                    InputStream replacement = bodies.getAllValues().get(1).contentStreamProvider().newStream()) {
                assertThat(replacement.readAllBytes()).isNotEqualTo(original.readAllBytes());
            }
        }
        ArgumentCaptor<HeadObjectRequest> heads = ArgumentCaptor.forClass(HeadObjectRequest.class);
        verify(client, times(headCount)).headObject(heads.capture());
        assertThat(heads.getAllValues()).allSatisfy(request -> {
            assertThat(request.bucket()).isEqualTo(BUCKET);
            assertThat(request.key()).isEqualTo(key);
        });
        ArgumentCaptor<DeleteObjectRequest> deletes = ArgumentCaptor.forClass(DeleteObjectRequest.class);
        verify(client, times(deleteCount)).deleteObject(deletes.capture());
        List<DeleteObjectRequest> requests = deletes.getAllValues();
        assertThat(requests).allSatisfy(request -> {
            assertThat(request.bucket()).isEqualTo(BUCKET);
            assertThat(request.key()).isEqualTo(key);
            assertThat(request.key()).isNotEqualTo(MANAGED_KEY);
        });
        // Only finally cleanup is unconditional, always on the same isolated probe.
        DeleteObjectRequest cleanup = requests.get(requests.size() - 1);
        assertThat(cleanup.overrideConfiguration()).isEmpty();
        assertThat(headers(cleanup)).isEmpty();
        for (int index = 0; index < requests.size() - 1; index++) {
            // The stale header MUST be exactly the genuine first HEAD ETag, never fabricated.
            assertConditional(requests.get(index), index == 0 ? STALE_ETAG : ETAG);
        }
        verifyNoMoreInteractions(client);
    }

    private static void assertConditional(DeleteObjectRequest request, String etag) {
        assertThat(request.overrideConfiguration()).isPresent();
        assertThat(headers(request)).isEqualTo(Map.of("If-Match", List.of(etag)));
    }

    private static Map<String, List<String>> headers(DeleteObjectRequest request) {
        return request.overrideConfiguration().map(configuration -> configuration.headers()).orElse(Map.of());
    }

    private static HeadObjectResponse head(String etag) {
        HeadObjectResponse.Builder response = HeadObjectResponse.builder().eTag(etag);
        response.sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build());
        return response.build();
    }

    private static AwsServiceException failure(int status) {
        S3Exception.Builder builder = S3Exception.builder();
        builder.statusCode(status);
        builder.message("private storage endpoint");
        return builder.build();
    }

    private static DeleteObjectResponse okDelete() {
        DeleteObjectResponse.Builder response = DeleteObjectResponse.builder();
        response.sdkHttpResponse(SdkHttpResponse.builder().statusCode(200).build());
        return response.build();
    }
}
