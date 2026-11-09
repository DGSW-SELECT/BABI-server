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
        jdbc.update("UPDATE users SET status='WITHDRAWN',withdrawn_at=CURRENT_TIMESTAMP WHERE id=?", ownerId);
        assertStatus(403, request("GET", recordsUrl()+"?date=2026-11-08", ownerId, null));
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
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                .timeout(Duration.ofSeconds(15)).header("Content-Type", "application/json");
        if (userId != null) request.header("Authorization", "Bearer " + jwt.createAccessToken(userId.toString()));
        return client.send(request.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    private void assertStatus(int expected, HttpResponse<String> response) {
        assertEquals(expected, response.statusCode(), response.body());
    }
}
