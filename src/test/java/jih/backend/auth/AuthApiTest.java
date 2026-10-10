package jih.backend.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import jakarta.servlet.http.Cookie;
import jih.backend.auth.controller.RefreshTokenCookie;
import jih.backend.support.IntegrationTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@AutoConfigureMockMvc
public class AuthApiTest extends IntegrationTestSupport {

    private static final String SIGNUP_BODY = """
            {"email": "player1@clutch.gg", "password": "clutch1234", "nickname": "player_one"}
            """;

    private static final String LOGIN_BODY = """
            {"email": "player1@clutch.gg", "password": "clutch1234"}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("가입하면 201, 액세스 토큰은 본문으로, 리프레시 토큰은 httpOnly 쿠키로 받는다")
    void signup_returnsTokens() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SIGNUP_BODY))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.expiresIn").value(1800))
            .andExpect(jsonPath("$.user.publicId").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("player1@clutch.gg"))
            // 내부 정보와 리프레시 토큰은 본문에 없어야 한다
            .andExpect(jsonPath("$.user.id").doesNotExist())
            .andExpect(jsonPath("$.user.passwordHash").doesNotExist())
            .andExpect(jsonPath("$.refreshToken").doesNotExist())
            .andExpect(header().string(
                HttpHeaders.SET_COOKIE, containsString(RefreshTokenCookie.NAME + "=")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("HttpOnly")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("SameSite=Strict")))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Path=/api/v1/auth")));
    }

    @Test
    @DisplayName("입력 형식이 틀리면 400과 필드별 사유를 받는다")
    void signup_invalidInput() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"email": "not-an-email", "password": "short", "nickname": "a"}
                                """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
            .andExpect(jsonPath("$.errors[*].field").value(hasItems("email", "password", "nickname")));
    }

    @Test
    @DisplayName("JSON 문법이 틀리면 400 MALFORMED_REQUEST")
    void signup_malformedJson() throws Exception {
        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": "))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("MALFORMED_REQUEST"));
    }

    @Test
    @DisplayName("중복 가입은 409 DUPLICATE_EMAIL")
    void signup_duplicate() throws Exception {
        signup();

        mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SIGNUP_BODY))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("DUPLICATE_EMAIL"));
    }

    @Test
    @DisplayName("틀린 비밀번호로 로그인하면 401 INVALID_CREDENTIALS")
    void login_wrongPassword() throws Exception {
        signup();

        mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                                {"email": "player1@clutch.gg", "password": "wrong1234"}
                                """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("로그인 후 액세스 토큰으로 보호 API를 호출할 수 있다")
    void login_thenAccessProtectedApi() throws Exception {
        signup();
        MvcResult login = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(LOGIN_BODY))
            .andExpect(status().isOk())
            .andReturn();

        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken(login))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.email").value("player1@clutch.gg"))
            .andExpect(jsonPath("$.nickname").value("player_one"));
    }

    @Test
    @DisplayName("토큰 없이 보호 API를 호출하면 401, 공통 에러 형식으로 응답한다")
    void protectedApi_withoutToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("위조된 토큰으로 호출하면 401")
    void protectedApi_withForgedToken() throws Exception {
        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer("eyJhbGciOiJIUzI1NiJ9.e30.forged")))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    @DisplayName("리프레시 쿠키로 재발급하면 새 쿠키를 받는다")
    void refresh_withCookie() throws Exception {
        MvcResult signup = signup();
        Cookie oldCookie = refreshCookie(signup);

        MvcResult refreshed = mockMvc.perform(post("/api/v1/auth/refresh").cookie(oldCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andReturn();

        Cookie newCookie = refreshCookie(refreshed);
        assertThat(newCookie.getValue()).isNotEqualTo(oldCookie.getValue());
    }

    @Test
    @DisplayName("쿠키 없이 재발급하면 401 INVALID_REFRESH_TOKEN")
    void refresh_withoutCookie() throws Exception {
        mockMvc.perform(post("/api/v1/auth/refresh"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    @Test
    @DisplayName("로그아웃 후에는 기존 액세스 토큰과 리프레시 쿠키를 모두 쓸 수 없다")
    void logout_invalidatesTokens() throws Exception {
        MvcResult signup = signup();
        String accessToken = accessToken(signup);
        Cookie refreshCookie = refreshCookie(signup);

        mockMvc.perform(post("/api/v1/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .cookie(refreshCookie))
            .andExpect(status().isNoContent())
            .andExpect(header().string(HttpHeaders.SET_COOKIE, containsString("Max-Age=0")));

        mockMvc.perform(get("/api/v1/users/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/v1/auth/refresh").cookie(refreshCookie))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));
    }

    // ---------- helpers ----------

    private MvcResult signup() throws Exception {
        return mockMvc.perform(post("/api/v1/auth/signup")
                .contentType(MediaType.APPLICATION_JSON)
                .content(SIGNUP_BODY))
            .andExpect(status().isCreated())
            .andReturn();
    }

    private static String accessToken(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static Cookie refreshCookie(MvcResult result) {
        Cookie cookie = result.getResponse().getCookie(RefreshTokenCookie.NAME);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
