package com.select.domain.baby.photo;

import com.select.domain.baby.entity.*;
import com.select.domain.baby.repository.*;
import com.select.domain.baby.service.BabyPhotoService;
import com.select.domain.user.entity.User;
import com.select.domain.user.repository.UserRepository;
import com.select.global.exception.BusinessException;
import com.select.global.exception.ErrorCode;
import com.select.global.security.jwt.JwtProvider;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.time.LocalDate;
import java.util.UUID;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class BabyPhotoIntegrationTest {
    @Autowired BabyPhotoService service;
    @Autowired UserRepository users;
    @Autowired BabyRepository babies;
    @Autowired BabyMemberRepository members;
    @Autowired BabyPhotoRepository photos;
    @Autowired JwtProvider jwt;
    @LocalServerPort int port;

    private User user() {
        return users.saveAndFlush(User.builder().email(UUID.randomUUID()+"@example.invalid").nickname("test").passwordHash("hash").build());
    }
    private byte[] image(int color) throws Exception {
        var image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB);
        image.setRGB(0, 0, color);
        var out = new ByteArrayOutputStream(); ImageIO.write(image, "png", out); return out.toByteArray();
    }
    private MockMultipartFile file(int color) throws Exception {
        return new MockMultipartFile("file", "photo.png", "image/png", image(color));
    }

    @Test void ownerReplacesGuardianReadsAndRemovedMemberCannotRead() throws Exception {
        var owner=user(); var guardian=user(); var outsider=user();
        var baby=babies.saveAndFlush(Baby.builder().name("baby").birthDate(LocalDate.now()).createdByUserId(owner.getId()).build());
        var o=members.saveAndFlush(BabyMember.builder().babyId(baby.getId()).userId(owner.getId()).role(BabyRole.OWNER).build());
        var g=members.saveAndFlush(BabyMember.builder().babyId(baby.getId()).userId(guardian.getId()).role(BabyRole.GUARDIAN).build());
        assertNull(service.get(baby.getId(), owner.getId()));
        service.save(baby.getId(), owner.getId(), file(0));
        byte[] original=service.get(baby.getId(), guardian.getId()); assertNotNull(original);
        service.save(baby.getId(), owner.getId(), file(0xff0000));
        assertFalse(java.util.Arrays.equals(original, service.get(baby.getId(), guardian.getId())));
        assertEquals(ErrorCode.ACCESS_DENIED, assertThrows(BusinessException.class, () -> service.save(baby.getId(), guardian.getId(), file(0))).getErrorCode());
        assertEquals(ErrorCode.BABY_MEMBER_NOT_FOUND, assertThrows(BusinessException.class, () -> service.get(baby.getId(), outsider.getId())).getErrorCode());
        g.deactivate(); members.saveAndFlush(g);
        assertThrows(BusinessException.class, () -> service.get(baby.getId(), guardian.getId()));
        o.changeRole(BabyRole.GUARDIAN); members.saveAndFlush(o);
        assertThrows(BusinessException.class, () -> service.delete(baby.getId(), owner.getId()));
        o.changeRole(BabyRole.OWNER); members.saveAndFlush(o);
        service.delete(baby.getId(), owner.getId()); service.delete(baby.getId(), owner.getId());
        assertNull(service.get(baby.getId(), owner.getId()));
    }

    @Test void multipartUploadReplaceAndDeleteThroughAuthenticatedApi() throws Exception {
        var owner = user();
        var baby = babies.saveAndFlush(Baby.builder().name("baby").birthDate(LocalDate.now()).createdByUserId(owner.getId()).build());
        members.saveAndFlush(BabyMember.builder().babyId(baby.getId()).userId(owner.getId()).role(BabyRole.OWNER).build());
        var uri = URI.create("http://localhost:" + port + "/babies/" + baby.getId() + "/photo");
        String token = jwt.createAccessToken(owner.getId().toString());
        var http = HttpClient.newHttpClient();
        byte[] previous = null;
        for (int color : new int[]{0, 0xff0000}) {
            String boundary = "photo-test-boundary";
            var multipart = new ByteArrayOutputStream();
            multipart.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"photo.png\"\r\nContent-Type: image/png\r\n\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            multipart.write(image(color));
            multipart.write(("\r\n--" + boundary + "--\r\n").getBytes(java.nio.charset.StandardCharsets.UTF_8));
            var upload = HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + token)
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.ofByteArray(multipart.toByteArray())).build();
            var response = http.send(upload, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), response.body());
            var download = http.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + token).GET().build(), HttpResponse.BodyHandlers.ofByteArray());
            assertEquals(200, download.statusCode());
            if (previous != null) assertFalse(java.util.Arrays.equals(previous, download.body()));
            previous = download.body();
        }
        assertEquals(401, http.send(HttpRequest.newBuilder(uri).GET().build(), HttpResponse.BodyHandlers.ofString()).statusCode());
        var removed = http.send(HttpRequest.newBuilder(uri).header("Authorization", "Bearer " + token).DELETE().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, removed.statusCode(), removed.body());
        assertNull(service.get(baby.getId(), owner.getId()));
        var docs = http.send(HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/v3/api-docs")).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, docs.statusCode());
        assertTrue(docs.body().contains("/babies/{babyId}/photo"));
        assertTrue(docs.body().contains("image/png"));
    }

    @Test void invalidImageCannotReplaceStoredPhotoAndHttpSupportsImageAccept() throws Exception {
        var owner=user();
        var baby=babies.saveAndFlush(Baby.builder().name("baby").birthDate(LocalDate.now()).createdByUserId(owner.getId()).build());
        members.saveAndFlush(BabyMember.builder().babyId(baby.getId()).userId(owner.getId()).role(BabyRole.OWNER).build());
        service.save(baby.getId(), owner.getId(), file(0));
        byte[] before=service.get(baby.getId(),owner.getId());
        assertEquals(ErrorCode.PROFILE_PHOTO_INVALID, assertThrows(BusinessException.class, () -> service.save(baby.getId(), owner.getId(), new MockMultipartFile("file",new byte[]{1,2}))).getErrorCode());
        assertArrayEquals(before,service.get(baby.getId(),owner.getId()));
        String token=jwt.createAccessToken(owner.getId().toString());
        var builder=HttpRequest.newBuilder(URI.create("http://localhost:"+port+"/babies/"+baby.getId()+"/photo")).header("Authorization","Bearer "+token).header("Accept","image/*");
        var http=HttpClient.newHttpClient();
        var response=http.send(builder.GET().build(),HttpResponse.BodyHandlers.ofByteArray());
        assertEquals(200,response.statusCode()); assertEquals("image/png",response.headers().firstValue("Content-Type").orElseThrow());
        assertTrue(response.headers().firstValue("Cache-Control").orElseThrow().contains("no-store"));
        service.delete(baby.getId(),owner.getId());
        assertEquals(404,http.send(builder.GET().build(),HttpResponse.BodyHandlers.ofString()).statusCode());
    }
}
