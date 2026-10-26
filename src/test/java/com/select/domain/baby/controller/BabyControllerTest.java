package com.select.domain.baby.controller;

import com.select.domain.baby.exception.BabyNotFoundException;
import com.select.domain.baby.exception.InvalidOwnershipTransferException;
import com.select.domain.baby.request.BabyCreateRequest;
import com.select.domain.baby.response.BabyCreateResponse;
import com.select.domain.baby.response.BabyReadResponse;
import com.select.domain.baby.response.BabyMemberResponse;
import com.select.domain.baby.entity.Baby;
import com.select.domain.baby.entity.BabyMember;
import com.select.domain.baby.entity.BabyRole;
import com.select.domain.baby.repository.BabyRepository;
import com.select.domain.baby.repository.BabyMemberRepository;
import com.select.domain.user.repository.UserRepository;
import com.select.domain.user.entity.User;
import com.select.domain.baby.service.BabyService;
import com.select.global.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.method.annotation.AuthenticationPrincipalArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BabyControllerTest {

    private static final Long BABY_ID = 101L;
    private static final Long USER_ID = 7L;
    private static final LocalDate BIRTH_DATE = LocalDate.of(2025, 1, 15);

    private BabyService babyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        babyService = mock(BabyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new BabyController(babyService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
        var context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(UsernamePasswordAuthenticationToken.authenticated(
                USER_ID.toString(), null, List.of()
        ));
        SecurityContextHolder.setContext(context);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createBabyReturnsBabyIdAndPassesAuthenticatedUserIdToService() throws Exception {
        BabyCreateRequest request = new BabyCreateRequest("아기", BIRTH_DATE);
        when(babyService.createBaby(request, USER_ID))
                .thenReturn(new BabyCreateResponse(BABY_ID, request.name(), BIRTH_DATE));

        mockMvc.perform(post("/babies")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"아기","birthDate":"2025-01-15"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.babyId").value(BABY_ID.intValue()))
                .andExpect(jsonPath("$.data.id").doesNotExist())
                .andExpect(jsonPath("$.data.name").value("아기"))
                .andExpect(jsonPath("$.data.birthDate").value("2025-01-15"));

        verify(babyService).createBaby(request, USER_ID);
    }

    @Test
    void getBabyReturnsBabyAndPassesAuthenticatedUserIdToService() throws Exception {
        when(babyService.getBaby(BABY_ID, USER_ID))
                .thenReturn(new BabyReadResponse(BABY_ID, "아기", BIRTH_DATE));

        mockMvc.perform(get("/babies/{babyId}", BABY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.babyId").value(BABY_ID.intValue()))
                .andExpect(jsonPath("$.data.name").value("아기"))
                .andExpect(jsonPath("$.data.birthDate").value("2025-01-15"));

        verify(babyService).getBaby(BABY_ID, USER_ID);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "1.5", "9223372036854775808"})
    void malformedBabyIdReturnsBadRequestWithoutCallingService(String babyId) throws Exception {
        mockMvc.perform(get("/babies/{babyId}", babyId))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details[0].field").value("babyId"));

        verifyNoInteractions(babyService);
    }

    @Test
    void getBabiesReturnsCommonResponseWithAllBabiesForAuthenticatedUser() throws Exception {
        when(babyService.getBabies(USER_ID)).thenReturn(List.of(
                new BabyReadResponse(BABY_ID, "아기", BIRTH_DATE),
                new BabyReadResponse(202L, "둘째", BIRTH_DATE.plusDays(10))
        ));

        mockMvc.perform(get("/babies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("아기 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].babyId").value(BABY_ID.intValue()))
                .andExpect(jsonPath("$.data[0].name").value("아기"))
                .andExpect(jsonPath("$.data[0].birthDate").value("2025-01-15"))
                .andExpect(jsonPath("$.data[1].babyId").value(202))
                .andExpect(jsonPath("$.data[1].name").value("둘째"))
                .andExpect(jsonPath("$.data[1].birthDate").value("2025-01-25"))
                .andExpect(jsonPath("$.data[*].id").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(babyService).getBabies(USER_ID);
    }

    @Test
    void getBabiesWithoutBabiesReturnsEmptyArrayInCommonResponse() throws Exception {
        when(babyService.getBabies(USER_ID)).thenReturn(List.of());

        mockMvc.perform(get("/babies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("아기 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data").isEmpty())
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(babyService).getBabies(USER_ID);
    }

    @ParameterizedTest
    @EnumSource(BabyRole.class)
    void getBabyMembersReturnsCommonResponseForEitherGuardianRole(BabyRole role) throws Exception {
        Long requestingUserId = role == BabyRole.OWNER ? USER_ID : 8L;
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(requestingUserId.toString(), null, List.of())
        );
        when(babyService.getBabyMembers(BABY_ID, requestingUserId)).thenReturn(List.of(
                new BabyMemberResponse(USER_ID, "보호자1", BabyRole.OWNER),
                new BabyMemberResponse(8L, "보호자2", BabyRole.MEMBER)
        ));

        mockMvc.perform(get("/babies/{babyId}/members", BABY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("아기 보호자 목록 조회에 성공했습니다."))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(2))
                .andExpect(jsonPath("$.data[0].userId").value(USER_ID.intValue()))
                .andExpect(jsonPath("$.data[0].nickname").value("보호자1"))
                .andExpect(jsonPath("$.data[0].role").value("OWNER"))
                .andExpect(jsonPath("$.data[1].userId").value(8))
                .andExpect(jsonPath("$.data[1].nickname").value("보호자2"))
                .andExpect(jsonPath("$.data[1].role").value("MEMBER"))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(babyService).getBabyMembers(BABY_ID, requestingUserId);
    }

    @Test
    void getBabyMembersWithoutMembershipReturnsForbidden() throws Exception {
        when(babyService.getBabyMembers(BABY_ID, USER_ID))
                .thenThrow(new AccessDeniedException("접근 권한이 없습니다."));

        mockMvc.perform(get("/babies/{babyId}/members", BABY_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(babyService).getBabyMembers(BABY_ID, USER_ID);
    }

    @Test
    void getBabyMembersWithMissingBabyReturnsNotFound() throws Exception {
        when(babyService.getBabyMembers(BABY_ID, USER_ID)).thenThrow(new BabyNotFoundException());

        mockMvc.perform(get("/babies/{babyId}/members", BABY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        verify(babyService).getBabyMembers(BABY_ID, USER_ID);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void getBabyMembersWithMissingUsersReturnsInternalServerError(boolean partiallyMissing) throws Exception {
        BabyRepository babyRepository = mock(BabyRepository.class);
        BabyMemberRepository memberRepository = mock(BabyMemberRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        when(babyRepository.findById(BABY_ID)).thenReturn(Optional.of(mock(Baby.class)));
        when(memberRepository.existsByBabyIdAndUserId(BABY_ID, USER_ID)).thenReturn(true);
        when(memberRepository.findByBabyId(BABY_ID)).thenReturn(List.of(
                BabyMember.builder().babyId(BABY_ID).userId(USER_ID).role(BabyRole.OWNER).build(),
                BabyMember.builder().babyId(BABY_ID).userId(8L).role(BabyRole.MEMBER).build()
        ));
        List<User> users = List.of();
        if (partiallyMissing) {
            User existingUser = mock(User.class);
            when(existingUser.getId()).thenReturn(USER_ID);
            when(existingUser.getNickname()).thenReturn("보호자1");
            users = List.of(existingUser);
        }
        when(userRepository.findAllById(List.of(USER_ID, 8L))).thenReturn(users);
        MockMvc realServiceMockMvc = MockMvcBuilders.standaloneSetup(new BabyController(
                        new BabyService(babyRepository, memberRepository, userRepository)))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        realServiceMockMvc.perform(get("/babies/{babyId}/members", BABY_ID))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_SERVER_ERROR"))
                .andExpect(jsonPath("$.message").value("서버에 오류가 발생했습니다."))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(result -> assertInstanceOf(IllegalStateException.class, result.getResolvedException()));

        verify(userRepository).findAllById(List.of(USER_ID, 8L));
    }

    @Test
    void missingBabyReturnsNotFound() throws Exception {
        when(babyService.getBaby(BABY_ID, USER_ID)).thenThrow(new BabyNotFoundException());

        mockMvc.perform(get("/babies/{babyId}", BABY_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));

        verify(babyService).getBaby(BABY_ID, USER_ID);
    }

    @Test
    void userWithoutAccessReturnsForbidden() throws Exception {
        when(babyService.getBaby(BABY_ID, USER_ID))
                .thenThrow(new AccessDeniedException("접근 권한이 없습니다."));

        mockMvc.perform(get("/babies/{babyId}", BABY_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));

        verify(babyService).getBaby(BABY_ID, USER_ID);
    }
    @Test
    void leaveBabyForAuthenticatedMemberReturnsCommonSuccessResponse() throws Exception {
        Long memberUserId = 8L;
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(memberUserId.toString(), null, List.of())
        );
        BabyMemberRepository memberRepository = mock(BabyMemberRepository.class);
        BabyMember member = BabyMember.builder()
                .babyId(BABY_ID).userId(memberUserId).role(BabyRole.MEMBER).build();
        when(memberRepository.findByBabyIdAndUserId(BABY_ID, memberUserId)).thenReturn(Optional.of(member));

        leaveBabyMvc(memberRepository).perform(delete("/babies/{babyId}/members/me", BABY_ID))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("보호자 그룹에서 탈퇴했습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(memberRepository).findByBabyIdAndUserId(BABY_ID, memberUserId);
        verify(memberRepository).delete(same(member));
        verifyNoMoreInteractions(memberRepository);
    }

    @ParameterizedTest
    @ValueSource(longs = {7, 99})
    void leaveBabyForOwnerOrNonMemberReturnsForbidden(Long requestingUserId) throws Exception {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(requestingUserId.toString(), null, List.of())
        );
        BabyMemberRepository memberRepository = mock(BabyMemberRepository.class);
        Optional<BabyMember> membership = requestingUserId.equals(USER_ID)
                ? Optional.of(BabyMember.builder().babyId(BABY_ID).userId(USER_ID).role(BabyRole.OWNER).build())
                : Optional.empty();
        when(memberRepository.findByBabyIdAndUserId(BABY_ID, requestingUserId)).thenReturn(membership);

        leaveBabyMvc(memberRepository).perform(delete("/babies/{babyId}/members/me", BABY_ID))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(result -> assertInstanceOf(AccessDeniedException.class, result.getResolvedException()));

        verify(memberRepository).findByBabyIdAndUserId(BABY_ID, requestingUserId);
        verify(memberRepository, never()).delete(any(BabyMember.class));
        verifyNoMoreInteractions(memberRepository);
    }

    @Test
    void transferOwnershipReturnsCommonResponseAndPassesAuthenticatedUserAndTarget() throws Exception {
        mockMvc.perform(patch("/babies/{babyId}/members/owner", BABY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newOwnerUserId\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("보호자 관리권한을 위임하였습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error").doesNotExist());

        verify(babyService).transferOwnership(BABY_ID, USER_ID, 8L);
        verifyNoMoreInteractions(babyService);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"newOwnerUserId\":null}"})
    void transferOwnershipRejectsMissingOrNullTargetBeforeCallingService(String body) throws Exception {
        mockMvc.perform(patch("/babies/{babyId}/members/owner", BABY_ID)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details.length()").value(1))
                .andExpect(jsonPath("$.error.details[0].field").value("newOwnerUserId"));

        verifyNoInteractions(babyService);
    }

    @Test
    void transferOwnershipMapsServiceAccessDeniedToForbiddenResponse() throws Exception {
        doThrow(new AccessDeniedException("관리자가 아닙니다"))
                .when(babyService).transferOwnership(BABY_ID, USER_ID, 8L);

        mockMvc.perform(patch("/babies/{babyId}/members/owner", BABY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newOwnerUserId\":8}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("접근 권한이 없습니다."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(result -> assertInstanceOf(AccessDeniedException.class, result.getResolvedException()));

        verify(babyService).transferOwnership(BABY_ID, USER_ID, 8L);
        verifyNoMoreInteractions(babyService);
    }

    @Test
    void transferOwnershipToSelfReturnsBadRequestBeforeRepositoryAccess() throws Exception {
        BabyRepository babyRepository = mock(BabyRepository.class);
        BabyMemberRepository memberRepository = mock(BabyMemberRepository.class);
        UserRepository userRepository = mock(UserRepository.class);
        BabyService realService = new BabyService(babyRepository, memberRepository, userRepository);
        MockMvc realServiceMockMvc = MockMvcBuilders.standaloneSetup(new BabyController(realService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();

        realServiceMockMvc.perform(patch("/babies/{babyId}/members/owner", BABY_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"newOwnerUserId\":7}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.data").value(nullValue()))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.error.details").isEmpty())
                .andExpect(result -> assertInstanceOf(InvalidOwnershipTransferException.class, result.getResolvedException()));

        verifyNoInteractions(babyRepository, memberRepository, userRepository);
    }

    private MockMvc leaveBabyMvc(BabyMemberRepository memberRepository) {
        BabyService realService = new BabyService(
                mock(BabyRepository.class), memberRepository, mock(UserRepository.class)
        );
        return MockMvcBuilders.standaloneSetup(new BabyController(realService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setCustomArgumentResolvers(new AuthenticationPrincipalArgumentResolver())
                .build();
    }

}
