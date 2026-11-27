package com.select.domain.care;

import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.service.BabyService;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.security.jwt.JwtProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.datasource.url=jdbc:h2:mem:care-records;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
        "spring.jpa.hibernate.ddl-auto=validate"
})
public class CareRecordIntegrationTest {
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private UserRepository users;
    @Autowired private BabyService babies;
    @Autowired private BabyMemberRepository members;
    @Autowired private JwtProvider jwt;
    @Autowired private ObjectMapper mapper;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private Long babyId, ownerId, writerId, readerId, outsiderId, ownerMemberId, writerMemberId;

    @BeforeEach
    void fixture() {
        ownerId = user("owner");
        writerId = user("writer");
        readerId = user("reader");
        outsiderId = user("outsider");
        babyId = babies.createBaby(new BabyCreateRequest("아기", LocalDate.of(2026, 1, 1)), ownerId).babyId();
        ownerMemberId = members.findByBabyIdAndUserId(babyId, ownerId).orElseThrow().getId();
        writerMemberId = member(writerId, BabyRole.GUARDIAN, "CARE_ALL");
        member(readerId, BabyRole.VIEWER, "CARE_READ");
    }

    @Test
    void allRecordTypesAreSharedWithTheirActualAuthorsInChronologicalOrder() throws Exception {
        create(ownerId, "FEEDING", "2026-11-08T08:00:00", Map.of("feedingType", "FORMULA", "amountMl", 120));
        var temperature = create(writerId, "TEMPERATURE", "2026-11-08T09:00:00", Map.of("temperatureCelsius", 36.5, "authorMemberId", ownerMemberId));
        assertEquals(writerMemberId.longValue(), temperature.path("authorMemberId").asLong());
        create(writerId, "SLEEP", "2026-11-07T23:00:00", Map.of("sleepEndAt", "2026-11-08T07:00:00"));
        create(ownerId, "DIAPER", "2026-11-08T10:00:00", Map.of("diaperType", "URINE"));
        create(ownerId, "CRY", "2026-11-08T11:00:00", Map.of("cryType", "HUNGER", "confidence", 80.25));
        var records = list(readerId, "").path("records");
        assertEquals(5, records.size());
        assertEquals("SLEEP", records.get(0).path("recordType").asString());
        assertEquals("FEEDING", records.get(1).path("recordType").asString());
        assertEquals("writer", records.get(2).path("authorNickname").asString());
        assertEquals(120, records.get(1).path("amountMl").asInt());
        assertEquals(36.5, records.get(2).path("temperatureCelsius").asDouble());
        assertEquals(1, list(ownerId, "&recordType=CRY").path("records").size());
        assertEquals(2, list(ownerId, "&authorMemberId=" + writerMemberId).path("records").size());
        var first = list(ownerId, "&size=2");
        assertEquals(2, first.path("records").size());
        assertTrue(first.path("hasNext").asBoolean());
        assertEquals("TEMPERATURE", list(ownerId, "&size=2&page=1").path("records").get(0).path("recordType").asString());
        assertFalse(list(ownerId, "&size=2&page=2").path("hasNext").asBoolean());
        assertEquals(0, list(ownerId, "&size=2&page=3").path("records").size());
    }

    @Test
    void readOnlyRemovedWithdrawnAndUnrelatedAccountsCannotWriteOrReadPrivateRecords() throws Exception {
        create(ownerId, "FEEDING", "2026-11-08T08:00:00", Map.of("feedingType", "BREAST_MILK"));
        String payload = "{\"recordType\":\"DIAPER\",\"occurredAt\":\"2026-11-08T10:00:00\",\"diaperType\":\"STOOL\"}";
        assertStatus(403, request("POST", recordsUrl(), readerId, payload));
        assertStatus(403, request("GET", recordsUrl() + "?date=2026-11-08", outsiderId, null));
        assertStatus(403, request("POST", recordsUrl(), outsiderId, payload));
        jdbc.update("UPDATE baby_members SET removed_at=CURRENT_TIMESTAMP WHERE id=?", writerMemberId);
        assertStatus(403, request("POST", recordsUrl(), writerId, payload));
        assertStatus(403, request("GET", recordsUrl()+"?date=2026-11-08", writerId, null));
        String ownerToken = jwt.createAccessToken(ownerId.toString());
        jdbc.update("UPDATE users SET status='WITHDRAWN',withdrawn_at=CURRENT_TIMESTAMP WHERE id=?", ownerId);
        var withdrawnResponse = requestWithToken("GET", recordsUrl()+"?date=2026-11-08", ownerToken, null);
        assertTrue(withdrawnResponse.statusCode() == 401 || withdrawnResponse.statusCode() == 403,
                withdrawnResponse.body());
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM feeding_records WHERE baby_id=?", Long.class, babyId));
    }

    @Test
    void viewerCannotWriteEvenWithAnAccidentalCareAllGrant() throws Exception {
        Long readerMember = members.findByBabyIdAndUserId(babyId, readerId).orElseThrow().getId();
        jdbc.update("INSERT INTO baby_permissions(baby_id,baby_member_id,permission) VALUES (?,?,'CARE_ALL')", babyId, readerMember);
        assertStatus(403, request("POST", "/api/v1/babies/"+babyId+"/feedings", readerId,
                "{\"occurredAt\":\"2026-11-08T08:00:00\",\"feedingType\":\"FORMULA\",\"amountMl\":120}"));
    }

    @Test
    void writeOnlyCaregiverCanRegisterButCannotReadRecords() throws Exception {
        Long writeOnlyId = user("write-only");
        member(writeOnlyId, BabyRole.GUARDIAN, "CARE_WRITE");
        var response = request("POST", recordsUrl(), writeOnlyId,
                "{\"recordType\":\"DIAPER\",\"occurredAt\":\"2026-11-08T10:00:00\",\"diaperType\":\"STOOL\"}");
        assertStatus(201, response);
        assertStatus(403, request("GET", recordsUrl() + "?date=2026-11-08", writeOnlyId, null));
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM diaper_records WHERE baby_id=?", Long.class, babyId));
    }

    @Test
    void dateBoundariesAndStablePaginationDoNotMixBabiesOrLoseEqualTimeRecords() throws Exception {
        create(ownerId,"DIAPER","2026-11-07T23:59:59",Map.of("diaperType","URINE"));
        create(ownerId,"DIAPER","2026-11-08T00:00:00",Map.of("diaperType","STOOL"));
        create(ownerId,"DIAPER","2026-11-09T00:00:00",Map.of("diaperType","URINE"));
        var second = create(writerId,"DIAPER","2026-11-08T00:00:00",Map.of("diaperType","MIXED"));
        assertEquals(2, list(readerId, "").path("records").size());
        assertEquals(second.path("recordId").asLong(), list(readerId, "&size=1&page=1").path("records").get(0).path("recordId").asLong());
        Long otherBaby = babies.createBaby(new BabyCreateRequest("다른 아기",LocalDate.of(2026,1,1)),outsiderId).babyId();
        assertStatus(403, request("GET","/api/v1/babies/"+otherBaby+"/records?date=2026-11-08",ownerId,null));
    }

    @Test
    void feedingEndpointUsesSameStorageAndAuthorChecks() throws Exception {
        var response = request("POST","/api/v1/babies/"+babyId+"/feedings",writerId,
                "{\"occurredAt\":\"2026-11-08T08:00:00\",\"feedingType\":\"FORMULA\",\"amountMl\":90}");
        assertStatus(201,response);
        assertEquals("FEEDING",mapper.readTree(response.body()).path("data").path("recordType").asString());
        assertEquals(90,list(readerId, "").path("records").get(0).path("amountMl").asInt());
    }

    @Test
    void feedingShortcutRejectsDatesOutsideTheDatabaseRange() throws Exception {
        assertStatus(400, request("POST", "/api/v1/babies/" + babyId + "/feedings", ownerId,
                "{\"occurredAt\":\"0000-11-08T08:00:00\",\"feedingType\":\"FORMULA\",\"amountMl\":90}"));
        assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM feeding_records WHERE baby_id=?", Long.class, babyId));
    }

    @Test
    void feedingCanBeListedPartiallyUpdatedAndDeleted() throws Exception {
        String url = "/api/v1/babies/" + babyId + "/feedings";
        var created = request("POST", url, writerId, """
                {"occurredAt":"2026-11-08T08:00:00","feedingType":"FORMULA","amountMl":90,"note":"아침"}
                """);
        var saved = assertSuccessResponse(201, created, "수유 기록을 등록했습니다.");
        String recordUrl = url + "/" + saved.path("recordId").asLong();
        create(ownerId, "TEMPERATURE", "2026-11-08T08:00:00", Map.of("temperatureCelsius", 36.5));

        var updated = request("PATCH", recordUrl, ownerId, """
                {"amountMl":120,"note":""}
                """);
        var record = assertSuccessResponse(200, updated, "수유 기록을 수정했습니다.");
        assertEquals(120, record.path("amountMl").asInt());
        assertEquals("FORMULA", record.path("feedingType").asString());
        assertEquals(writerMemberId.longValue(), record.path("authorMemberId").asLong());
        assertTrue(record.path("note").isNull());

        var listed = request("GET", url + "?date=2026-11-08&authorMemberId=" + writerMemberId, readerId, null);
        var page = assertSuccessResponse(200, listed, "수유 기록을 조회했습니다.");
        assertEquals(1, page.path("records").size());
        assertEquals(120, page.path("records").get(0).path("amountMl").asInt());
        assertTrue(assertSuccessResponse(200, request("DELETE", recordUrl, writerId, null),
                "수유 기록을 삭제했습니다.").isNull());
        assertStatus(404, request("PATCH", recordUrl, ownerId, "{\"amountMl\":120}"));
        assertEquals(0, list(readerId, "&recordType=FEEDING").path("records").size());
        assertEquals(1, list(readerId, "&recordType=TEMPERATURE").path("records").size());
    }

    @Test
    void overnightSleepCanBeCompletedAndDeleted() throws Exception {
        String url = "/api/v1/babies/" + babyId + "/sleeps";
        assertStatus(400, request("POST", url, writerId, """
                {"occurredAt":"2026-11-07T23:00:00","sleepEndAt":"2026-11-07T22:00:00"}
                """));
        var created = request("POST", url, writerId, """
                {"occurredAt":"2026-11-07T23:00:00","note":"밤잠"}
                """);
        var saved = assertSuccessResponse(201, created, "수면 기록을 등록했습니다.");
        assertTrue(saved.path("sleepEndAt").isNull());
        String recordUrl = url + "/" + saved.path("recordId").asLong();
        String nextDay = url + "?date=2026-11-08";
        var ongoing = assertSuccessResponse(200, request("GET", nextDay, readerId, null), "수면 기록을 조회했습니다.");
        assertEquals(1, ongoing.path("records").size());
        assertStatus(400, request("PATCH", recordUrl, writerId, "{\"sleepEndAt\":\"2026-11-07T22:30:00\"}"));
        var completed = request("PATCH", recordUrl, writerId, "{\"sleepEndAt\":\"2026-11-08T06:30:00\"}");
        var completedRecord = assertSuccessResponse(200, completed, "수면 기록을 수정했습니다.");
        assertEquals("2026-11-08T06:30:00", completedRecord.path("sleepEndAt").asString());
        assertEquals("밤잠", completedRecord.path("note").asString());
        assertStatus(400, request("PATCH", recordUrl, writerId, "{\"occurredAt\":\"2026-11-08T07:00:00\"}"));
        var listed = assertSuccessResponse(200, request("GET", nextDay, readerId, null), "수면 기록을 조회했습니다.");
        assertEquals(completedRecord, listed.path("records").get(0));
        assertTrue(assertSuccessResponse(200, request("DELETE", recordUrl, writerId, null),
                "수면 기록을 삭제했습니다.").isNull());
        assertEquals(0, assertSuccessResponse(200, request("GET", nextDay, readerId, null),
                "수면 기록을 조회했습니다.").path("records").size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"feedings", "sleeps"})
    void feedingAndSleepWritesAreRestrictedToTheAuthorizedBaby(String collection) throws Exception {
        boolean feeding = collection.equals("feedings");
        var record = create(ownerId, feeding ? "FEEDING" : "SLEEP", "2026-11-08T08:00:00",
                feeding ? Map.of("feedingType", "FORMULA") : Map.of());
        long recordId = record.path("recordId").asLong();
        String url = "/api/v1/babies/" + babyId + "/" + collection;
        String recordUrl = url + "/" + recordId;
        Long otherBaby = babies.createBaby(new BabyCreateRequest("다른 아기", LocalDate.of(2026, 1, 1)), outsiderId).babyId();
        String anotherBabyUrl = "/api/v1/babies/" + otherBaby + "/" + collection + "/" + recordId;
        String patch = "{\"note\":\"수정\"}";
        assertStatus(401, request("GET", url + "?date=2026-11-08", null, null));
        assertStatus(403, request("GET", url + "?date=2026-11-08", outsiderId, null));
        assertStatus(403, request("PATCH", recordUrl, readerId, patch));
        assertStatus(403, request("DELETE", recordUrl, readerId, null));
        assertStatus(404, request("PATCH", anotherBabyUrl, outsiderId, patch));
        assertStatus(404, request("DELETE", anotherBabyUrl, outsiderId, null));
        assertStatus(400, request("PATCH", recordUrl, ownerId, "{}"));
        if (feeding) assertStatus(400, request("PATCH", recordUrl, ownerId, "{\"amountMl\":-1}"));
        assertEquals(record, list(readerId, "&recordType=" + (feeding ? "FEEDING" : "SLEEP")).path("records").get(0));
    }

    @Test
    void temperaturesAreSavedWithTheAuthenticatedAuthorAndListedByMeasurementDate() throws Exception {
        String url = temperaturesUrl();
        create(ownerId, "FEEDING", "2026-11-08T08:00:00", Map.of("feedingType", "FORMULA"));
        create(ownerId, "TEMPERATURE", "2026-11-09T00:00:00", Map.of("temperatureCelsius", 37.0));
        create(ownerId, "TEMPERATURE", "2026-11-08T12:00:00", Map.of("temperatureCelsius", 36.8));

        var saved = request("POST", url, writerId, """
                {"occurredAt":"2026-11-08T09:00:00","temperatureCelsius":36.5,
                 "note":"기상 후 측정","authorMemberId":%d}
                """.formatted(ownerMemberId));
        var savedRecord = assertSuccessResponse(201, saved, "체온 기록을 등록했습니다.");
        assertTrue(savedRecord.isObject());
        assertEquals("TEMPERATURE", savedRecord.path("recordType").asString());
        assertEquals(writerMemberId.longValue(), savedRecord.path("authorMemberId").asLong());

        var firstResponse = request("GET", url + "?date=2026-11-08&size=1", readerId, null);
        var firstPage = assertSuccessResponse(200, firstResponse, "체온 기록을 조회했습니다.");
        assertTrue(firstPage.path("records").isArray());
        assertTrue(firstPage.path("hasNext").asBoolean());
        assertEquals(1, firstPage.path("records").size());
        var listedRecord = firstPage.path("records").get(0);
        assertEquals(savedRecord.path("recordId").asLong(), listedRecord.path("recordId").asLong());
        assertEquals(36.5, listedRecord.path("temperatureCelsius").asDouble());
        assertEquals("기상 후 측정", listedRecord.path("note").asString());

        var nextResponse = request("GET", url + "?date=2026-11-08&size=1&page=1", readerId, null);
        assertStatus(200, nextResponse);
        var nextPage = mapper.readTree(nextResponse.body()).path("data");
        assertFalse(nextPage.path("hasNext").asBoolean());
        assertEquals(1, nextPage.path("records").size());
        assertEquals(36.8, nextPage.path("records").get(0).path("temperatureCelsius").asDouble());

        var emptyResponse = request("GET", url + "?date=2026-11-10", readerId, null);
        assertStatus(200, emptyResponse);
        assertEquals(0, mapper.readTree(emptyResponse.body()).path("data").path("records").size());
    }

    @Test
    void temperatureEndpointsEnforceReadAndWritePermissions() throws Exception {
        String url = temperaturesUrl();
        String listUrl = url + "?date=2026-11-08";
        String body = """
                {"occurredAt":"2026-11-08T09:00:00","temperatureCelsius":36.5}
                """;
        assertStatus(401, request("POST", url, null, body));
        assertStatus(401, request("GET", listUrl, null, null));
        assertStatus(403, request("POST", url, readerId, body));
        assertStatus(403, request("POST", url, outsiderId, body));
        assertStatus(403, request("GET", listUrl, outsiderId, null));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM temperature_records WHERE baby_id=?", Long.class, babyId));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{\"temperatureCelsius\":36.5}",
            "{\"occurredAt\":\"2026-11-08T09:00:00\"}",
            "{\"occurredAt\":\"2026-11-08T09:00:00\",\"temperatureCelsius\":45.1}",
            "{\"occurredAt\":\"2026-11-08T09:00:00\",\"temperatureCelsius\":36.55}"
    })
    void temperatureEndpointRejectsInvalidMeasurements(String body) throws Exception {
        assertStatus(400, request("POST", temperaturesUrl(), ownerId, body));
        assertEquals(0L, jdbc.queryForObject(
                "SELECT COUNT(*) FROM temperature_records WHERE baby_id=?", Long.class, babyId));
    }

    @Test
    void temperatureCanBePartiallyUpdatedMovedToAnotherDayAndDeleted() throws Exception {
        var record = create(writerId, "TEMPERATURE", "2026-11-08T09:00:00",
                Map.of("temperatureCelsius", 36.5, "note", "기상 후 측정"));
        String url = temperaturesUrl() + "/" + record.path("recordId").asLong();
        var updated = request("PATCH", url, ownerId, """
                {"temperatureCelsius":36.8}
                """);
        var updatedRecord = assertSuccessResponse(200, updated, "체온 기록을 수정했습니다.");
        assertTrue(updatedRecord.isObject());
        assertEquals(36.8, updatedRecord.path("temperatureCelsius").asDouble());
        assertEquals("기상 후 측정", updatedRecord.path("note").asString());
        assertEquals(record.path("occurredAt").asString(), updatedRecord.path("occurredAt").asString());
        assertEquals(writerMemberId.longValue(), updatedRecord.path("authorMemberId").asLong());
        assertEquals(record.path("createdAt").asString(), updatedRecord.path("createdAt").asString());
        assertStatus(200, request("PATCH", url, ownerId, "{\"temperatureCelsius\":36.8}"));

        var moved = request("PATCH", url, writerId, """
                {"occurredAt":"2026-11-09T00:00:00","note":""}
                """);
        assertStatus(200, moved);
        assertTrue(mapper.readTree(moved.body()).path("data").path("note").isNull());
        assertEquals(0, list(readerId, "&recordType=TEMPERATURE").path("records").size());
        var nextDay = request("GET", temperaturesUrl() + "?date=2026-11-09", readerId, null);
        assertStatus(200, nextDay);
        assertEquals(36.8, mapper.readTree(nextDay.body()).path("data").path("records")
                .get(0).path("temperatureCelsius").asDouble());

        create(ownerId, "TEMPERATURE", "2026-11-09T08:00:00", Map.of("temperatureCelsius", 37.0));
        var deleted = request("DELETE", url, writerId, null);
        assertTrue(assertSuccessResponse(200, deleted, "체온 기록을 삭제했습니다.").isNull());
        var afterDeletion = request("GET", temperaturesUrl() + "?date=2026-11-09", readerId, null);
        assertStatus(200, afterDeletion);
        var remaining = mapper.readTree(afterDeletion.body()).path("data").path("records");
        assertEquals(1, remaining.size());
        assertEquals(37.0, remaining.get(0).path("temperatureCelsius").asDouble());
        var missing = request("DELETE", url, writerId, null);
        assertStatus(404, missing);
        var failure = mapper.readTree(missing.body());
        assertEquals(mapper.readTree("false"), failure.path("success"));
        assertEquals("체온 기록을 찾을 수 없습니다.", failure.path("message").asString());
        assertTrue(failure.path("data").isNull());
        assertEquals("TEMPERATURE_RECORD_NOT_FOUND", failure.path("error").path("code").asString());
        assertStatus(404, request("PATCH", url, writerId, "{\"temperatureCelsius\":36.9}"));
    }

    @Test
    void temperatureMutationsCheckWritePermissionAndScopeByBaby() throws Exception {
        var record = create(ownerId, "TEMPERATURE", "2026-11-08T09:00:00", Map.of("temperatureCelsius", 36.5));
        long recordId = record.path("recordId").asLong();
        String url = temperaturesUrl() + "/" + recordId;
        Long otherBaby = babies.createBaby(new BabyCreateRequest("다른 아기", LocalDate.of(2026, 1, 1)), outsiderId).babyId();
        String anotherBabyUrl = "/api/v1/babies/" + otherBaby + "/temperatures/" + recordId;
        String body = "{\"temperatureCelsius\":36.9}";

        assertStatus(401, request("PATCH", url, null, body));
        assertStatus(401, request("DELETE", url, null, null));
        assertStatus(403, request("PATCH", url, readerId, body));
        assertStatus(403, request("DELETE", url, readerId, null));
        assertStatus(403, request("PATCH", url, outsiderId, body));
        assertStatus(403, request("DELETE", url, outsiderId, null));
        assertStatus(404, request("PATCH", anotherBabyUrl, outsiderId, body));
        assertStatus(404, request("DELETE", anotherBabyUrl, outsiderId, null));
        assertEquals(36.5, list(readerId, "&recordType=TEMPERATURE").path("records").get(0)
                .path("temperatureCelsius").asDouble());
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "{}",
            "{\"occurredAt\":null,\"temperatureCelsius\":null,\"note\":null}",
            "{\"occurredAt\":\"0000-11-08T09:00:00\"}",
            "{\"temperatureCelsius\":29.9}",
            "{\"temperatureCelsius\":36.55}"
    })
    void temperaturePatchRejectsInvalidChanges(String body) throws Exception {
        var record = create(ownerId, "TEMPERATURE", "2026-11-08T09:00:00", Map.of("temperatureCelsius", 36.5));
        assertStatus(400, request("PATCH", temperaturesUrl() + "/" + record.path("recordId").asLong(), ownerId, body));
        assertEquals(36.5, list(readerId, "&recordType=TEMPERATURE").path("records").get(0)
                .path("temperatureCelsius").asDouble());
    }

    @Test
    void voiceLinkMustBelongToSameBabyAndAuthenticatedAuthor() throws Exception {
        jdbc.update("INSERT INTO voice_inputs(baby_id,baby_member_id,transcript,parse_status) VALUES (?,?,'test','PARSED')",babyId,ownerMemberId);
        Long voiceId=jdbc.queryForObject("SELECT id FROM voice_inputs WHERE baby_id=?",Long.class,babyId);
        create(ownerId,"FEEDING","2026-11-08T08:00:00",Map.of("feedingType","FORMULA","voiceInputId",voiceId));
        var bad=Map.of("recordType","FEEDING","occurredAt","2026-11-08T08:00:00","feedingType","FORMULA","voiceInputId",voiceId);
        assertStatus(400,request("POST",recordsUrl(),writerId,mapper.writeValueAsString(bad)));
        assertEquals(1L,jdbc.queryForObject("SELECT COUNT(*) FROM feeding_records WHERE baby_id=?",Long.class,babyId));
    }

    @ParameterizedTest
    @ValueSource(strings={
        "{}",
        "{\"recordType\":\"UNKNOWN\",\"occurredAt\":\"2026-11-08T08:00:00\"}",
        "{\"recordType\":\"FEEDING\",\"occurredAt\":\"2026-11-08T08:00:00\",\"feedingType\":\"FORMULA\",\"amountMl\":-1}",
        "{\"recordType\":\"FEEDING\",\"occurredAt\":\"2026-11-08T08:00:00\",\"feedingType\":\"FORMULA\",\"temperatureCelsius\":36.5}",
        "{\"recordType\":\"TEMPERATURE\",\"occurredAt\":\"2026-11-08T08:00:00\",\"temperatureCelsius\":29.9}",
        "{\"recordType\":\"TEMPERATURE\",\"occurredAt\":\"2026-11-08T08:00:00\",\"temperatureCelsius\":36.55}",
        "{\"recordType\":\"SLEEP\",\"occurredAt\":\"2026-11-08T08:00:00\",\"sleepEndAt\":\"2026-11-08T07:00:00\"}",
        "{\"recordType\":\"DIAPER\",\"occurredAt\":\"2026-11-08T08:00:00\"}",
        "{\"recordType\":\"CRY\",\"occurredAt\":\"2026-11-08T08:00:00\",\"confidence\":100.1}",
        "{\"recordType\":\"CRY\",\"occurredAt\":\"2026-11-08T08:00:00\",\"voiceInputId\":1}"
    })
    void malformedOrInconsistentRecordsAreRejected(String body) throws Exception {
        assertStatus(400,request("POST",recordsUrl(),ownerId,body));
        assertEquals(0, list(ownerId, "").path("records").size());
    }

    @ParameterizedTest
    @ValueSource(strings={"&page=-1","&page=10001","&size=0","&size=101","&authorMemberId=-1","&recordType=BAD"})
    void invalidListParametersReturn400(String parameters) throws Exception {
        assertStatus(400,request("GET",recordsUrl()+"?date=2026-11-08"+parameters,ownerId,null));
    }

    @Test
    void allEndpointsRequireAuthentication() throws Exception {
        assertStatus(401,request("GET",recordsUrl()+"?date=2026-11-08",null,null));
        assertStatus(401,request("POST",recordsUrl(),null,"{}"));
        assertStatus(401,request("POST","/api/v1/babies/"+babyId+"/feedings",null,"{}"));
    }

    private Long user(String nickname) {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID() + "@example.invalid")
                .passwordHash("unused-test-hash").nickname(nickname).build()).getId();
    }

    private Long member(Long userId, BabyRole role, String permission) {
        Long id = members.saveAndFlush(BabyMember.builder().babyId(babyId).userId(userId).role(role).build()).getId();
        jdbc.update("INSERT INTO baby_permissions(baby_id,baby_member_id,permission) VALUES (?,?,?)", babyId, id, permission);
        return id;
    }

    private String recordsUrl() { return "/api/v1/babies/" + babyId + "/records"; }

    private String temperaturesUrl() { return "/api/v1/babies/" + babyId + "/temperatures"; }

    private JsonNode create(Long userId, String type, String time, Map<String, Object> values) throws Exception {
        var body = new java.util.HashMap<>(values);
        body.put("recordType", type);
        body.put("occurredAt", time);
        var response = request("POST", recordsUrl(), userId, mapper.writeValueAsString(body));
        assertStatus(201, response);
        return mapper.readTree(response.body()).path("data");
    }

    private JsonNode list(Long userId, String query) throws Exception {
        var response = request("GET", recordsUrl() + "?date=2026-11-08" + query, userId, null);
        assertStatus(200, response);
        return mapper.readTree(response.body()).path("data");
    }

    private HttpResponse<String> request(String method, String path, Long userId, String body) throws Exception {
        String token = userId == null ? null : jwt.createAccessToken(userId.toString());
        return requestWithToken(method, path, token, body);
    }

    private HttpResponse<String> requestWithToken(String method, String path, String token, String body) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response.body());
    }

    private JsonNode assertSuccessResponse(int status, HttpResponse<String> response, String message) {
        assertStatus(status, response);
        var body = mapper.readTree(response.body());
        assertEquals(mapper.readTree("true"), body.path("success"));
        assertEquals(message, body.path("message").asString());
        assertTrue(body.path("error").isMissingNode());
        assertTrue(body.has("data"));
        return body.path("data");
    }
}
