package com.invi.api.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import com.invi.api.common.StorageUnavailableException;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Exception;
import software.amazon.awssdk.services.s3.model.S3Object;

@ExtendWith(MockitoExtension.class)
class MediaCleanupServiceTest {
    private static final String BUCKET = "invi-media";
    private static final String PREFIX = "invitations/";
    private static final UUID INVITATION = UUID.fromString("11111111-1111-4111-8111-111111111111");
    private static final String OWNER_PREFIX = PREFIX + INVITATION + "/";
    private static final String KEY = OWNER_PREFIX + "22222222-2222-4222-8222-222222222222.webp";
    private static final String SECOND_KEY = OWNER_PREFIX + "33333333-3333-4333-8333-333333333333.jpg";
    private static final String OTHER_KEY = "invitations/44444444-4444-4444-8444-444444444444/"
            + "55555555-5555-4555-8555-555555555555.png";
    private static final Instant MODIFIED = Instant.parse("2026-10-01T08:00:00Z");
    private static final String ETAG = "\"inventory-etag\"";
    private static final MediaCleanupTransactions.Plan EMPTY_PLAN =
            new MediaCleanupTransactions.Plan(0, 0, 0, 0, 0, List.of());

    @Mock private ObjectProvider<S3Client> clientProvider;
    @Mock private S3Client client;
    @Mock private MediaCleanupTransactions transactions;
    @Mock private R2ConditionalDelete conditionalDelete;
    @Captor private ArgumentCaptor<List<MediaCleanupTransactions.Observation>> inventoryCaptor;

    @Test
    void boundDefaultsDisableScheduledCleanupWithoutEvenLookingForS3() {
        MediaCleanupProperties defaults = new Binder(new MapConfigurationPropertySource(Map.of()))
                .bindOrCreate("invi.r2.cleanup", Bindable.of(MediaCleanupProperties.class));
        assertThat(defaults).isEqualTo(new MediaCleanupProperties(false, true, Duration.ofDays(7), 10000, 100));
        MediaCleanupService service = new MediaCleanupService(clientProvider, transactions, defaults, conditionalDelete, BUCKET);

        assertThat(service.scheduledRun()).isEqualTo(new MediaCleanupReport(true, 168, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        verifyNoInteractions(clientProvider, client, transactions, conditionalDelete);
    }

    @Test
    void disabledScheduleIgnoresPhysicalDeletionFlagAndMissingBucket() {
        MediaCleanupService service = new MediaCleanupService(clientProvider, transactions,
                properties(false, false, 10000), conditionalDelete, "");

        assertThat(service.scheduledRun()).isEqualTo(new MediaCleanupReport(true, 168, 0, 0, 0, 0, 0, 0, 0, 0, 0));

        verifyNoInteractions(clientProvider, client, transactions, conditionalDelete);
    }

    @Test
    void enabledScheduleFailsClosedWhenS3IsUnavailable() {
        MediaCleanupService service = new MediaCleanupService(clientProvider, transactions,
                properties(true, false, 10000), conditionalDelete, BUCKET);

        assertThatThrownBy(service::scheduledRun).isInstanceOf(StorageUnavailableException.class);

        verify(clientProvider).getIfAvailable();
        verifyNoInteractions(client, transactions, conditionalDelete);
    }

    @Test
    void previewFailsClosedWhenBucketIsBlank() {
        when(clientProvider.getIfAvailable()).thenReturn(client);
        MediaCleanupService service = new MediaCleanupService(clientProvider, transactions,
                properties(true, false, 10000), conditionalDelete, "  ");

        assertThatThrownBy(() -> service.preview(INVITATION)).isInstanceOf(StorageUnavailableException.class);

        verifyNoInteractions(client, transactions, conditionalDelete);
    }

    @Test
    void previewAlwaysUsesDryRunAndInvitationPrefixEvenWhenPhysicalCleanupIsEnabled() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(object(KEY)));
        when(transactions.observe(anyList(), eq(OWNER_PREFIX))).thenReturn(plan(KEY));

        MediaCleanupReport report = service.preview(INVITATION);

        assertThat(report.dryRun()).isTrue();
        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isZero();
        verify(client).listObjectsV2(listRequest(OWNER_PREFIX, null));
        verify(transactions).observe(List.of(observation(KEY)), OWNER_PREFIX);
        verifyNoMoreInteractions(client, transactions, conditionalDelete);
    }

    @Test
    void previewStillInventoriesWhenSchedulingIsDisabled() {
        when(clientProvider.getIfAvailable()).thenReturn(client);
        MediaCleanupService service = new MediaCleanupService(clientProvider, transactions,
                properties(false, false, 10000), conditionalDelete, BUCKET);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page());
        when(transactions.observe(List.of(), OWNER_PREFIX)).thenReturn(EMPTY_PLAN);

        assertThat(service.preview(INVITATION).dryRun()).isTrue();

        verify(client).listObjectsV2(listRequest(OWNER_PREFIX, null));
        verify(transactions).observe(List.of(), OWNER_PREFIX);
        verifyNoMoreInteractions(client, transactions, conditionalDelete);
    }

    @Test
    void scheduledDryRunObservesGlobalPrefixWithoutHeadClaimOrDelete() {
        MediaCleanupService service = configuredService(true, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(object(KEY)));
        when(transactions.observe(anyList(), eq(PREFIX))).thenReturn(plan(KEY));

        assertThat(service.scheduledRun()).isEqualTo(new MediaCleanupReport(true, 168, 1, 0, 7, 2, 3, 2, 1, 0, 0));

        verify(client).listObjectsV2(listRequest(PREFIX, null));
        verify(transactions).observe(List.of(observation(KEY)), PREFIX);
        verifyNoMoreInteractions(client, transactions, conditionalDelete);
    }

    @Test
    void completePaginationObservesOnlyManagedInScopeKeysAfterAllPages() {
        MediaCleanupService service = configuredService(true, 10000);
        S3Object unknown = S3Object.builder().key(OWNER_PREFIX + "legacy.gif").build();
        S3Object noKey = S3Object.builder().build();
        S3Object outOfScope = S3Object.builder().key("templates/thumbnail.png").build();
        when(client.listObjectsV2(listRequest(OWNER_PREFIX, null))).thenReturn(
                ListObjectsV2Response.builder().contents(object(KEY), unknown, noKey)
                        .isTruncated(true).nextContinuationToken("page-two").build());
        when(client.listObjectsV2(listRequest(OWNER_PREFIX, "page-two"))).thenReturn(
                page(object(SECOND_KEY), object(OTHER_KEY), outOfScope));
        when(transactions.observe(anyList(), eq(OWNER_PREFIX))).thenReturn(EMPTY_PLAN);

        MediaCleanupReport report = service.preview(INVITATION);

        assertThat(report.scanned()).isEqualTo(6);
        assertThat(report.skippedUnmanaged()).isEqualTo(4);
        InOrder order = inOrder(client, transactions, conditionalDelete);
        order.verify(client).listObjectsV2(listRequest(OWNER_PREFIX, null));
        order.verify(client).listObjectsV2(listRequest(OWNER_PREFIX, "page-two"));
        order.verify(transactions).observe(inventoryCaptor.capture(), eq(OWNER_PREFIX));
        assertThat(inventoryCaptor.getValue()).containsExactly(observation(KEY), observation(SECOND_KEY));
        order.verifyNoMoreInteractions();
    }

    @Test
    void repeatedContinuationTokenAbortsBeforeAnyObservationOrDeletion() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(listRequest(PREFIX, null))).thenReturn(
                truncatedPage("repeated", object(KEY)));
        when(client.listObjectsV2(listRequest(PREFIX, "repeated"))).thenReturn(
                truncatedPage("repeated", object(SECOND_KEY)));

        assertInventoryAborted(service);

        verify(client).listObjectsV2(listRequest(PREFIX, null));
        verify(client).listObjectsV2(listRequest(PREFIX, "repeated"));
        verifyNoMoreInteractions(client);
    }

    @Test
    void missingContinuationTokenAbortsEvenAfterReceivingAValidObject() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(truncatedPage(null, object(KEY)));

        assertInventoryAborted(service);
    }

    @Test
    void missingTokenOnLaterPageDoesNotObserveEarlierCompleteObjects() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(listRequest(PREFIX, null))).thenReturn(truncatedPage("next", object(KEY)));
        when(client.listObjectsV2(listRequest(PREFIX, "next"))).thenReturn(truncatedPage(null, object(SECOND_KEY)));

        assertInventoryAborted(service);
    }

    @Test
    void maxObjectsCountsUnmanagedObjectsAndAbortsRatherThanReturningPartialPlan() {
        MediaCleanupService service = configuredService(false, 1);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                page(object(KEY), S3Object.builder().key("unknown").build()));

        assertInventoryAborted(service);
    }

    @Test
    void maxObjectsLimitAppliesAcrossPages() {
        MediaCleanupService service = configuredService(false, 1);
        when(client.listObjectsV2(listRequest(PREFIX, null))).thenReturn(truncatedPage("next", object(KEY)));
        when(client.listObjectsV2(listRequest(PREFIX, "next"))).thenReturn(page(object(SECOND_KEY)));

        assertInventoryAborted(service);
    }

    @Test
    void exactlyMaxObjectsIsAllowed() {
        MediaCleanupService service = configuredService(true, 1);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(object(KEY)));
        when(transactions.observe(anyList(), eq(PREFIX))).thenReturn(EMPTY_PLAN);

        assertThat(service.scheduledRun().scanned()).isEqualTo(1);

        verify(transactions).observe(List.of(observation(KEY)), PREFIX);
        verify(client, never()).headObject(any(HeadObjectRequest.class));
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(conditionalDelete, never()).deleteIfUnchanged(any(), any(), any(), any());
    }

    @Test
    void sdkInventoryFailureAbortsWithoutPlanningOrDeletion() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class)))
                .thenThrow(SdkClientException.create("inventory unavailable"));

        assertInventoryAborted(service);
    }

    @Test
    void sdkFailureOnLaterPageDoesNotObservePartialInventory() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(listRequest(PREFIX, null))).thenReturn(truncatedPage("next", object(KEY)));
        when(client.listObjectsV2(listRequest(PREFIX, "next")))
                .thenThrow(SdkClientException.create("second page unavailable"));

        assertInventoryAborted(service);
    }

    @Test
    void missingInventoryResponseFailsClosed() {
        MediaCleanupService service = configuredService(false, 10000);
        // Mockito's default null response deliberately models an incomplete SDK response.
        assertInventoryAborted(service);
    }

    @Test
    void missingListModificationTimeFailsClosed() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                page(object(KEY), S3Object.builder().key(SECOND_KEY).eTag(ETAG).build()));

        assertInventoryAborted(service);
    }

    @Test
    void missingListEtagFailsClosed() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                page(S3Object.builder().key(KEY).lastModified(MODIFIED).build()));

        assertInventoryAborted(service);
    }

    @Test
    void blankListEtagFailsClosed() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                page(S3Object.builder().key(KEY).lastModified(MODIFIED).eTag("  ").build()));

        assertInventoryAborted(service);
    }

    @Test
    void referenceScanTransactionFailurePropagatesWithoutHeadClaimOrDelete() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(object(KEY)));
        IllegalStateException failure = new IllegalStateException("reference scan unavailable");
        when(transactions.observe(anyList(), eq(PREFIX))).thenThrow(failure);

        assertThatThrownBy(service::scheduledRun).isSameAs(failure);

        verify(transactions).observe(List.of(observation(KEY)), PREFIX);
        verify(client).listObjectsV2(listRequest(PREFIX, null));
        verifyNoMoreInteractions(client, transactions, conditionalDelete);
    }

    @Test
    void physicalRunWithNoCandidatesNeverProbesOrDeletes() {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(page(object(KEY)));
        when(transactions.observe(anyList(), eq(PREFIX))).thenReturn(EMPTY_PLAN);

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.dryRun()).isFalse();
        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isZero();
        verifyNoInteractions(conditionalDelete);
        verify(client).listObjectsV2(listRequest(PREFIX, null));
        verifyNoMoreInteractions(client);
        verify(transactions).observe(List.of(observation(KEY)), PREFIX);
        verifyNoMoreInteractions(transactions);
    }

    @Test
    void capabilityFailureAbortsBeforeHeadClaimDeleteOrFinish() {
        MediaCleanupService service = candidateService(KEY);
        StorageUnavailableException failure = new StorageUnavailableException("conditional delete unsupported");
        doThrow(failure).when(conditionalDelete).verifyCapability(client, BUCKET);

        assertThatThrownBy(service::scheduledRun).isSameAs(failure);

        InOrder order = inOrder(client, transactions, conditionalDelete);
        order.verify(client).listObjectsV2(listRequest(PREFIX, null));
        order.verify(transactions).observe(List.of(observation(KEY)), PREFIX);
        order.verify(conditionalDelete).verifyCapability(client, BUCKET);
        order.verifyNoMoreInteractions();
        verify(client, never()).headObject(any(HeadObjectRequest.class));
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(transactions, never()).claim(any(), any());
        verify(transactions, never()).finish(any());
        verify(transactions, never()).failed(any());
        verify(conditionalDelete, never()).deleteIfUnchanged(any(), any(), any(), any());
    }

    @Test
    void physicalDeletionRequiresHeadThenCommittedClaimThenDeleteThenConfirmedAbsenceThenFinish() {
        MediaCleanupService service = candidateService(KEY);
        MediaCleanupTransactions.Observation latest = new MediaCleanupTransactions.Observation(
                KEY, MODIFIED.plusSeconds(100), "\"new-head-etag\"");
        when(client.headObject(headRequest(KEY))).thenReturn(
                HeadObjectResponse.builder().lastModified(latest.modifiedAt()).eTag(latest.etag()).build())
                .thenThrow(S3Exception.builder().statusCode(404).build());
        MediaCleanupTransactions.Claim claim = claim(KEY, true);
        when(transactions.claim(KEY, latest)).thenReturn(claim);
        when(transactions.finish(claim)).thenReturn(true);

        assertThat(service.scheduledRun()).isEqualTo(new MediaCleanupReport(false, 168, 1, 0, 7, 2, 3, 2, 1, 1, 0));

        InOrder order = inOrder(client, transactions, conditionalDelete);
        order.verify(client).listObjectsV2(listRequest(PREFIX, null));
        order.verify(transactions).observe(List.of(observation(KEY)), PREFIX);
        order.verify(conditionalDelete).verifyCapability(client, BUCKET);
        order.verify(client).headObject(headRequest(KEY));
        order.verify(transactions).claim(KEY, latest);
        order.verify(conditionalDelete).deleteIfUnchanged(client, BUCKET, KEY, latest.etag());
        order.verify(client).headObject(headRequest(KEY));
        order.verify(transactions).finish(claim);
        order.verifyNoMoreInteractions();
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void successfulDeleteResponseWithObjectStillPresentKeepsFenceAndDoesNotFinish() {
        MediaCleanupService service = candidateService(KEY);
        MediaCleanupTransactions.Claim claim = claim(KEY, true);
        when(client.headObject(headRequest(KEY))).thenReturn(head(), head());
        when(transactions.claim(KEY, observation(KEY))).thenReturn(claim);
        // The conditional DELETE returns normally, but HEAD proves the object remains.

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isEqualTo(1);
        InOrder order = inOrder(client, transactions, conditionalDelete);
        order.verify(client).headObject(headRequest(KEY));
        order.verify(transactions).claim(KEY, observation(KEY));
        order.verify(conditionalDelete).deleteIfUnchanged(client, BUCKET, KEY, ETAG);
        order.verify(client).headObject(headRequest(KEY));
        order.verify(transactions).failed(claim);
        order.verifyNoMoreInteractions();
        verify(transactions, never()).finish(any());
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void postDeleteHeadOutageKeepsFenceAndDoesNotFinish() {
        MediaCleanupService service = candidateService(KEY);
        MediaCleanupTransactions.Claim claim = claim(KEY, true);
        when(client.headObject(headRequest(KEY))).thenReturn(head())
                .thenThrow(S3Exception.builder().statusCode(503).build());
        when(transactions.claim(KEY, observation(KEY))).thenReturn(claim);

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isEqualTo(1);
        InOrder order = inOrder(client, transactions, conditionalDelete);
        order.verify(client).headObject(headRequest(KEY));
        order.verify(transactions).claim(KEY, observation(KEY));
        order.verify(conditionalDelete).deleteIfUnchanged(client, BUCKET, KEY, ETAG);
        order.verify(client).headObject(headRequest(KEY));
        order.verify(transactions).failed(claim);
        order.verifyNoMoreInteractions();
        verify(transactions, never()).finish(any());
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void nullClaimSkipsObjectSavedMeanwhileWithoutDeletingOrFinishing() {
        MediaCleanupService service = candidateService(KEY);
        when(client.headObject(headRequest(KEY))).thenReturn(head());
        when(transactions.claim(KEY, observation(KEY))).thenReturn(null);

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isZero();
        verify(transactions).claim(KEY, observation(KEY));
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(conditionalDelete, never()).deleteIfUnchanged(any(), any(), any(), any());
        verify(transactions, never()).finish(any());
        verify(transactions, never()).failed(any());
    }

    @Test
    void head404ClaimsAbsenceAndFinalizesWithoutPhysicalDelete() {
        MediaCleanupService service = candidateService(KEY);
        when(client.headObject(headRequest(KEY))).thenThrow(S3Exception.builder().statusCode(404).build());
        MediaCleanupTransactions.Claim absent = claim(KEY, false);
        when(transactions.claim(KEY, null)).thenReturn(absent);
        when(transactions.finish(absent)).thenReturn(true);

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.deleted()).isEqualTo(1);
        assertThat(report.failed()).isZero();
        InOrder order = inOrder(client, transactions, conditionalDelete);
        order.verify(client).headObject(headRequest(KEY));
        order.verify(transactions).claim(KEY, null);
        order.verify(transactions).finish(absent);
        order.verifyNoMoreInteractions();
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(conditionalDelete, never()).deleteIfUnchanged(any(), any(), any(), any());
        verify(transactions, never()).failed(any());
    }

    @Test
    void headOutageDoesNotClaimOrDelete() {
        MediaCleanupService service = candidateService(KEY);
        when(client.headObject(headRequest(KEY))).thenThrow(S3Exception.builder().statusCode(503).build());

        assertCandidateRejectedBeforeClaim(service);
    }

    @Test
    void headSdkOutageDoesNotClaimOrDelete() {
        MediaCleanupService service = candidateService(KEY);
        when(client.headObject(headRequest(KEY))).thenThrow(SdkClientException.create("HEAD unavailable"));

        assertCandidateRejectedBeforeClaim(service);
    }

    @Test
    void missingHeadResponseDoesNotClaimOrDelete() {
        MediaCleanupService service = candidateService(KEY);

        assertCandidateRejectedBeforeClaim(service);
    }

    @Test
    void missingHeadModificationTimeDoesNotClaimOrDelete() {
        MediaCleanupService service = candidateService(KEY);
        when(client.headObject(headRequest(KEY))).thenReturn(HeadObjectResponse.builder().eTag(ETAG).build());

        assertCandidateRejectedBeforeClaim(service);
    }

    @Test
    void missingHeadEtagDoesNotClaimOrDelete() {
        MediaCleanupService service = candidateService(KEY);
        when(client.headObject(headRequest(KEY))).thenReturn(HeadObjectResponse.builder().lastModified(MODIFIED).build());

        assertCandidateRejectedBeforeClaim(service);
    }

    @Test
    void blankHeadEtagDoesNotClaimOrDelete() {
        MediaCleanupService service = candidateService(KEY);
        when(client.headObject(headRequest(KEY))).thenReturn(
                HeadObjectResponse.builder().lastModified(MODIFIED).eTag(" ").build());

        assertCandidateRejectedBeforeClaim(service);
    }

    @Test
    void claimReferenceRecheckFailureNeverDeletesOrFinalizes() {
        MediaCleanupService service = candidateService(KEY);
        when(client.headObject(headRequest(KEY))).thenReturn(head());
        when(transactions.claim(KEY, observation(KEY))).thenThrow(new IllegalStateException("references unavailable"));

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isEqualTo(1);
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(conditionalDelete, never()).deleteIfUnchanged(any(), any(), any(), any());
        verify(transactions, never()).finish(any());
        verify(transactions, never()).failed(any());
    }

    @Test
    void deleteFailureNotifiesFenceOwnerAndContinuesWithLaterCandidates() {
        MediaCleanupService service = candidateService(KEY, SECOND_KEY);
        MediaCleanupTransactions.Claim first = stubClaim(KEY);
        MediaCleanupTransactions.Claim second = stubClaim(SECOND_KEY);
        doThrow(SdkClientException.create("ambiguous delete result"))
                .when(conditionalDelete).deleteIfUnchanged(client, BUCKET, KEY, ETAG);
        when(transactions.finish(second)).thenReturn(true);

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.deleted()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        InOrder order = inOrder(client, transactions, conditionalDelete);
        order.verify(transactions).claim(KEY, observation(KEY));
        order.verify(conditionalDelete).deleteIfUnchanged(client, BUCKET, KEY, ETAG);
        order.verify(transactions).failed(first);
        order.verify(client).headObject(headRequest(SECOND_KEY));
        order.verify(transactions).claim(SECOND_KEY, observation(SECOND_KEY));
        order.verify(conditionalDelete).deleteIfUnchanged(client, BUCKET, SECOND_KEY, ETAG);
        order.verify(client).headObject(headRequest(SECOND_KEY));
        order.verify(transactions).finish(second);
        verify(transactions, never()).finish(first);
    }

    @Test
    void conditionalDeleteConflictRetainsFenceForRetryWithoutFinalizing() {
        MediaCleanupService service = candidateService(KEY);
        MediaCleanupTransactions.Claim claim = stubClaim(KEY);
        doThrow(S3Exception.builder().statusCode(412).build())
                .when(conditionalDelete).deleteIfUnchanged(client, BUCKET, KEY, ETAG);

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isEqualTo(1);
        InOrder order = inOrder(transactions, conditionalDelete);
        order.verify(transactions).claim(KEY, observation(KEY));
        order.verify(conditionalDelete).deleteIfUnchanged(client, BUCKET, KEY, ETAG);
        order.verify(transactions).failed(claim);
        verify(transactions, never()).finish(any());
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    void failureCallbackExceptionDoesNotStopLaterCandidates() {
        MediaCleanupService service = candidateService(KEY, SECOND_KEY);
        MediaCleanupTransactions.Claim first = stubClaim(KEY);
        MediaCleanupTransactions.Claim second = stubClaim(SECOND_KEY);
        doThrow(SdkClientException.create("delete unavailable"))
                .when(conditionalDelete).deleteIfUnchanged(client, BUCKET, KEY, ETAG);
        doThrow(new IllegalStateException("retry transaction unavailable")).when(transactions).failed(first);
        when(transactions.finish(second)).thenReturn(true);

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.deleted()).isEqualTo(1);
        assertThat(report.failed()).isEqualTo(1);
        verify(transactions).failed(first);
        verify(transactions).finish(second);
        verify(transactions, never()).finish(first);
    }

    @Test
    void finalizationExceptionTriggersFailedForTheSameCommittedClaim() {
        MediaCleanupService service = candidateService(KEY);
        MediaCleanupTransactions.Claim claim = stubClaim(KEY);
        when(transactions.finish(claim)).thenThrow(new IllegalStateException("finalization unavailable"));

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isEqualTo(1);
        InOrder order = inOrder(client, transactions, conditionalDelete);
        order.verify(transactions).claim(KEY, observation(KEY));
        order.verify(conditionalDelete).deleteIfUnchanged(client, BUCKET, KEY, ETAG);
        order.verify(client).headObject(headRequest(KEY));
        order.verify(transactions).finish(claim);
        order.verify(transactions).failed(claim);
    }

    @Test
    void rejectedFinalizationCountsFailureNotDeletion() {
        MediaCleanupService service = candidateService(KEY);
        MediaCleanupTransactions.Claim claim = stubClaim(KEY);
        when(transactions.finish(claim)).thenReturn(false);

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isEqualTo(1);
        verify(conditionalDelete).deleteIfUnchanged(client, BUCKET, KEY, ETAG);
        verify(transactions).finish(claim);
        verify(transactions, never()).failed(any());
    }

    @Test
    void reportExposesOnlyAggregateCountsAndNeverKeysUrlsOrCandidateDetails() {
        MediaCleanupService service = configuredService(true, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                page(object(KEY), S3Object.builder().key("https://private.example/secret?signature=hidden").build()));
        when(transactions.observe(anyList(), eq(PREFIX))).thenReturn(plan(KEY, SECOND_KEY));

        MediaCleanupReport report = service.scheduledRun();

        assertThat(report).isEqualTo(new MediaCleanupReport(true, 168, 2, 1, 7, 2, 3, 2, 1, 0, 0));
        assertThat(Arrays.stream(MediaCleanupReport.class.getRecordComponents()).map(component -> component.getName()))
                .containsExactly("dryRun", "graceHours", "scanned", "skippedUnmanaged", "tracked", "referenced",
                        "waiting", "eligible", "deleting", "deleted", "failed");
        assertThat(Arrays.stream(MediaCleanupReport.class.getRecordComponents()).map(component -> component.getType()))
                .allMatch(Class::isPrimitive);
        assertThat(report.toString()).doesNotContain(KEY, SECOND_KEY, INVITATION.toString(),
                "http", "signature", "hidden", BUCKET, ETAG);
    }

    private MediaCleanupService configuredService(boolean dryRun, int maxObjects) {
        when(clientProvider.getIfAvailable()).thenReturn(client);
        return new MediaCleanupService(clientProvider, transactions, properties(true, dryRun, maxObjects), conditionalDelete, BUCKET);
    }

    private MediaCleanupService candidateService(String... keys) {
        MediaCleanupService service = configuredService(false, 10000);
        when(client.listObjectsV2(any(ListObjectsV2Request.class))).thenReturn(
                page(Arrays.stream(keys).map(MediaCleanupServiceTest::object).toArray(S3Object[]::new)));
        when(transactions.observe(anyList(), eq(PREFIX))).thenReturn(plan(keys));
        return service;
    }

    private MediaCleanupTransactions.Claim stubClaim(String key) {
        MediaCleanupTransactions.Claim claim = claim(key, true);
        when(client.headObject(headRequest(key))).thenReturn(head())
                .thenThrow(S3Exception.builder().statusCode(404).build());
        when(transactions.claim(key, observation(key))).thenReturn(claim);
        return claim;
    }

    private void assertInventoryAborted(MediaCleanupService service) {
        assertThatThrownBy(service::scheduledRun).isInstanceOf(StorageUnavailableException.class);
        verifyNoInteractions(transactions, conditionalDelete);
        verify(client, never()).headObject(any(HeadObjectRequest.class));
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
        verify(conditionalDelete, never()).deleteIfUnchanged(any(), any(), any(), any());
    }

    private void assertCandidateRejectedBeforeClaim(MediaCleanupService service) {
        MediaCleanupReport report = service.scheduledRun();
        assertThat(report.deleted()).isZero();
        assertThat(report.failed()).isEqualTo(1);
        verify(transactions).observe(List.of(observation(KEY)), PREFIX);
        verifyNoMoreInteractions(transactions);
        verify(conditionalDelete).verifyCapability(client, BUCKET);
        verifyNoMoreInteractions(conditionalDelete);
        verify(client, never()).deleteObject(any(DeleteObjectRequest.class));
    }

    private static MediaCleanupProperties properties(boolean enabled, boolean dryRun, int maxObjects) {
        return new MediaCleanupProperties(enabled, dryRun, Duration.ofDays(7), maxObjects, 100);
    }

    private static MediaCleanupTransactions.Plan plan(String... keys) {
        return new MediaCleanupTransactions.Plan(7, 2, 3, 2, 1, List.of(keys));
    }

    private static MediaCleanupTransactions.Claim claim(String key, boolean exists) {
        return new MediaCleanupTransactions.Claim(key,
                UUID.fromString("aaaaaaaa-aaaa-4aaa-8aaa-aaaaaaaaaaaa"), exists);
    }

    private static MediaCleanupTransactions.Observation observation(String key) {
        return new MediaCleanupTransactions.Observation(key, MODIFIED, ETAG);
    }

    private static S3Object object(String key) {
        return S3Object.builder().key(key).lastModified(MODIFIED).eTag(ETAG).build();
    }

    private static HeadObjectResponse head() {
        return HeadObjectResponse.builder().lastModified(MODIFIED).eTag(ETAG).build();
    }

    private static ListObjectsV2Response page(S3Object... objects) {
        return ListObjectsV2Response.builder().contents(objects).isTruncated(false).build();
    }

    private static ListObjectsV2Response truncatedPage(String token, S3Object... objects) {
        return ListObjectsV2Response.builder().contents(objects).isTruncated(true).nextContinuationToken(token).build();
    }

    private static ListObjectsV2Request listRequest(String prefix, String token) {
        return ListObjectsV2Request.builder().bucket(BUCKET).prefix(prefix).maxKeys(1000).continuationToken(token).build();
    }

    private static HeadObjectRequest headRequest(String key) {
        return HeadObjectRequest.builder().bucket(BUCKET).key(key).build();
    }

}
