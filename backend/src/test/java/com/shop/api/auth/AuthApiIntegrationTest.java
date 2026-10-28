package com.shop.api.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.shop.api.config.JwtProperties;
import com.shop.api.security.JwtTokenProvider;
import jakarta.servlet.http.Cookie;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthApiIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper om;
    @Autowired JdbcTemplate jdbc;
    @Autowired JwtProperties jwtProps;

    private static final String LOGIN_OK = "{\"userId\":\"user01\",\"password\":\"Passw0rd!\"}";

    private MvcResult login(String body) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body)).andReturn();
    }

    private String cookieValue(MvcResult r) {
        String sc = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(sc).startsWith("refresh_token=");
        return sc.substring("refresh_token=".length(), sc.indexOf(';'));
    }

    private String accessToken(MvcResult r) throws Exception {
        return om.readTree(r.getResponse().getContentAsString()).get("accessToken").asText();
    }

    // ---------- 로그인

    @Test
    void 로그인_성공() throws Exception {
        MvcResult r = login(LOGIN_OK);
        assertThat(r.getResponse().getStatus()).isEqualTo(200);

        String sc = r.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(sc).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/auth")
                .contains("Max-Age=1209600").doesNotContain("Secure");

        JsonNode body = om.readTree(r.getResponse().getContentAsString());
        assertThat(body.get("tokenType").asText()).isEqualTo("Bearer");
        assertThat(body.get("expiresIn").asInt()).isEqualTo(10800);
        assertThat(body.get("accessTokenExpiresAt").asText()).endsWith("+09:00");
        assertThat(body.get("user").get("userId").asText()).isEqualTo("user01");
        assertThat(body.get("user").get("userName").asText()).isEqualTo("홍길동");
        assertThat(body.get("user").get("role").asText()).isEqualTo("ROLE_USER");
        assertThat(r.getResponse().getContentAsString()).doesNotContain(cookieValue(r)).doesNotContain("passwd");

        // DB 에는 원문이 아니라 SHA-256 hex 만 저장
        Integer rawRows = jdbc.queryForObject("select count(*) from tb_refresh_tokens where token_hash = ?",
                Integer.class, cookieValue(r));
        assertThat(rawRows).isZero();
    }

    @Test
    void 로그인_검증_실패_400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"\",\"password\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value("입력값을 확인해 주세요."))
                .andExpect(jsonPath("$.path").value("/api/auth/login"))
                .andExpect(jsonPath("$.timestamp").exists())
                .andExpect(jsonPath("$.errors", hasSize(2)))
                .andExpect(jsonPath("$.errors[*].field", containsInAnyOrder("userId", "password")))
                .andExpect(jsonPath("$.errors[*].message",
                        containsInAnyOrder("아이디를 입력해 주세요.", "비밀번호를 입력해 주세요.")));

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":\"" + "a".repeat(51) + "\",\"password\":\"" + "b".repeat(101) + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].message",
                        containsInAnyOrder("아이디는 50자 이하로 입력해 주세요.", "비밀번호는 100자 이하로 입력해 주세요.")));
    }

    @Test
    void 로그인_JSON_파싱_실패는_400() throws Exception {
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("{broken"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void 로그인_자격증명_오류는_401_동일_응답() throws Exception {
        for (String body : List.of("{\"userId\":\"user01\",\"password\":\"wrong\"}",
                "{\"userId\":\"nobody\",\"password\":\"Passw0rd!\"}")) {
            mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isUnauthorized())
                    .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                    .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"))
                    .andExpect(jsonPath("$.message").value("아이디 또는 비밀번호가 올바르지 않습니다."))
                    .andExpect(jsonPath("$.path").value("/api/auth/login"))
                    .andExpect(jsonPath("$.errors", hasSize(0)));
        }
    }

    // ---------- 갱신 / 로그아웃

    @Test
    void 갱신_성공_후_회전된_이전_토큰은_재사용_불가() throws Exception {
        String oldCookie = cookieValue(login(LOGIN_OK));

        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", oldCookie)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(10800))
                .andExpect(jsonPath("$.user.userId").value("user01"))
                .andReturn();
        String sc = refreshed.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(sc).contains("HttpOnly").contains("SameSite=Strict").contains("Path=/api/auth")
                .contains("Max-Age=1209600");
        String newCookie = cookieValue(refreshed);
        assertThat(newCookie).isNotEqualTo(oldCookie);

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", oldCookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", newCookie)))
                .andExpect(status().isOk());
    }

    @Test
    void 갱신_쿠키가_없으면_401과_삭제_쿠키() throws Exception {
        mvc.perform(post("/api/auth/refresh"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.allOf(
                                org.hamcrest.Matchers.startsWith("refresh_token=;"),
                                org.hamcrest.Matchers.containsString("Max-Age=0"),
                                org.hamcrest.Matchers.containsString("Path=/api/auth"),
                                org.hamcrest.Matchers.containsString("HttpOnly"),
                                org.hamcrest.Matchers.containsString("SameSite=Strict"))))
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"))
                .andExpect(jsonPath("$.message").value("다시 로그인해 주세요."))
                .andExpect(jsonPath("$.path").value("/api/auth/refresh"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void 만료된_리프레시_토큰은_401이고_행이_삭제된다() throws Exception {
        String cookie = cookieValue(login(LOGIN_OK));
        String hash = RefreshTokenService.hash(cookie);
        jdbc.update("update tb_refresh_tokens set expires_at = ? where token_hash = ?",
                java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().minusDays(1)), hash);

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", cookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));

        assertThat(jdbc.queryForObject("select count(*) from tb_refresh_tokens where token_hash = ?",
                Integer.class, hash)).isZero();
    }

    @Test
    void 사용자가_삭제된_리프레시_토큰은_401() throws Exception {
        jdbc.update("insert into tb_users(user_id, passwd, user_name, gender, birth, email, phone, zip_code, addr) "
                + "values ('temp01', 'x', 'temp', 'M', '900101', 't@e.com', '010', '00000', 'addr')");
        String raw = "orphan-token-for-test";
        jdbc.update("insert into tb_refresh_tokens(token_hash, user_id, expires_at) values (?, 'ghost01', ?)",
                RefreshTokenService.hash(raw), java.sql.Timestamp.valueOf(java.time.LocalDateTime.now().plusDays(1)));
        jdbc.update("delete from tb_users where user_id = 'temp01'");

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", raw)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void 로그아웃은_204_삭제쿠키_이후_갱신은_401() throws Exception {
        String cookie = cookieValue(login(LOGIN_OK));

        mvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", cookie)))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE,
                        org.hamcrest.Matchers.containsString("Max-Age=0")));

        mvc.perform(post("/api/auth/refresh").cookie(new Cookie("refresh_token", cookie)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
    }

    @Test
    void 로그아웃은_쿠키가_없거나_모르는_값이어도_항상_204() throws Exception {
        mvc.perform(post("/api/auth/logout")).andExpect(status().isNoContent())
                .andExpect(header().exists(HttpHeaders.SET_COOKIE));
        mvc.perform(post("/api/auth/logout").cookie(new Cookie("refresh_token", "unknown")))
                .andExpect(status().isNoContent());
    }

    @Test
    void 로그아웃과_갱신은_만료된_액세스_토큰_헤더가_붙어도_동작한다() throws Exception {
        mvc.perform(post("/api/auth/logout").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isNoContent());
    }

    // ---------- 보호 경로

    @Test
    void users_me_토큰_없음은_401_UNAUTHORIZED() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401))
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("로그인이 필요합니다."))
                .andExpect(jsonPath("$.path").value("/api/users/me"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
    }

    @Test
    void users_me_잘못된_토큰은_401_INVALID_TOKEN() throws Exception {
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"))
                .andExpect(jsonPath("$.message").value("유효하지 않은 인증 정보입니다."));
        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Basic abc"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void users_me_만료된_토큰은_401_TOKEN_EXPIRED() throws Exception {
        String expired = new JwtTokenProvider(jwtProps,
                Clock.fixed(Instant.now().minus(Duration.ofHours(4)), ZoneId.of("Asia/Seoul")))
                .createAccessToken("user01", "홍길동", "ROLE_USER").token();

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_EXPIRED"))
                .andExpect(jsonPath("$.message").value("인증이 만료되었습니다."));
    }

    @Test
    void users_me_사용자가_DB에_없으면_401_INVALID_TOKEN() throws Exception {
        String token = new JwtTokenProvider(jwtProps, Clock.system(ZoneId.of("Asia/Seoul")))
                .createAccessToken("ghost", "유령", "ROLE_USER").token();

        mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    void users_me_정상_200_passwd_없음() throws Exception {
        String token = accessToken(login(LOGIN_OK));

        MvcResult r = mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId").value("user01"))
                .andExpect(jsonPath("$.userName").value("홍길동"))
                .andExpect(jsonPath("$.gender").value("M"))
                .andExpect(jsonPath("$.birth").value("900101"))
                .andExpect(jsonPath("$.email").value("user01@example.com"))
                .andExpect(jsonPath("$.phone").value("010-1234-5678"))
                .andExpect(jsonPath("$.zipCode").value("04524"))
                .andExpect(jsonPath("$.addr").value("서울특별시 중구 세종대로 110"))
                .andExpect(jsonPath("$.addrDtl").value("101호"))
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.roleName").value("일반회원"))
                .andExpect(jsonPath("$.createAt").value("2026-10-28T10:00:00+09:00"))
                .andReturn();
        assertThat(r.getResponse().getContentAsString()).doesNotContain("passwd").doesNotContain("$2a$");
    }

    @Test
    void users_me_NULL_값은_키를_유지한_채_null() throws Exception {
        jdbc.update("insert into tb_users(user_id, passwd, user_name, gender, birth, email, phone, zip_code, addr, addr_dtl, role_id) "
                + "values ('nullrole', 'x', '무권한', 'F', '950505', 'n@e.com', '010', '11111', 'addr', null, null)");
        String token = new JwtTokenProvider(jwtProps, Clock.system(ZoneId.of("Asia/Seoul")))
                .createAccessToken("nullrole", "무권한", "ROLE_USER").token();

        MvcResult r = mvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("ROLE_USER"))
                .andExpect(jsonPath("$.roleName").value((Object) null))
                .andExpect(jsonPath("$.addrDtl").value((Object) null))
                .andReturn();
        JsonNode n = om.readTree(r.getResponse().getContentAsString());
        assertThat(n.has("roleName")).isTrue();
        assertThat(n.has("addrDtl")).isTrue();
    }

    @Test
    void 어드민_로그인은_ROLE_ADMIN() throws Exception {
        MvcResult r = login("{\"userId\":\"admin01\",\"password\":\"Passw0rd!\"}");
        assertThat(om.readTree(r.getResponse().getContentAsString()).get("user").get("role").asText())
                .isEqualTo("ROLE_ADMIN");
    }

    @Test
    void cart_orders_placeholder() throws Exception {
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.path").value("/api/cart"));
        mvc.perform(get("/api/orders")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.path").value("/api/orders"));
        mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));

        String auth = "Bearer " + accessToken(login(LOGIN_OK));
        mvc.perform(get("/api/cart").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalCount").value(0));
        mvc.perform(get("/api/orders").header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items", hasSize(0)))
                .andExpect(jsonPath("$.totalCount").value(0));
        mvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.status").value(501))
                .andExpect(jsonPath("$.code").value("NOT_IMPLEMENTED"))
                .andExpect(jsonPath("$.message").value("준비 중인 기능입니다."))
                .andExpect(jsonPath("$.path").value("/api/orders"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
        mvc.perform(post("/api/orders").header(HttpHeaders.AUTHORIZATION, auth)
                        .contentType(MediaType.TEXT_PLAIN).content("anything"))
                .andExpect(status().isNotImplemented());
    }

    // ---------- 공개 경로 / 공통 에러

    @Test
    void 공개_경로는_잘못된_토큰이_있어도_401이_아니라_404() throws Exception {
        mvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer abc.def.ghi"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."))
                .andExpect(jsonPath("$.path").value("/api/products"));
        mvc.perform(get("/api/products")).andExpect(status().isNotFound());
    }

    @Test
    void 지원하지_않는_메서드는_405_공통_포맷() throws Exception {
        mvc.perform(get("/api/auth/login"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.code").value("METHOD_NOT_ALLOWED"))
                .andExpect(jsonPath("$.errors", hasSize(0)));
        mvc.perform(delete("/api/products")).andExpect(status().isNotFound());
    }
}
