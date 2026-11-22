package com.tasknova;

import com.tasknova.entity.Task;
import com.tasknova.entity.User;
import com.tasknova.repository.TaskRepository;
import com.tasknova.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end behaviour of pages and APIs: every page renders, access rules hold,
 * and the bugs fixed in the 1.1 overhaul stay fixed.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AppBehaviourTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TaskRepository taskRepository;

    // ── helpers ─────────────────────────────────────────────────────

    private Cookie login(String identifier, String password) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"" + identifier + "\",\"password\":\"" + password + "\"}"))
                .andExpect(status().isOk())
                .andReturn();
        return res.getResponse().getCookie("tasknova_jwt");
    }

    private Cookie demo() throws Exception  { return login("demo@tasknova.com", "Demo@1234"); }
    private Cookie admin() throws Exception { return login("admin@tasknova.com", "Admin@1234"); }

    private long createTask(Cookie who, String title) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/tasks").cookie(who)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"" + title + "\",\"dueDate\":\"" + LocalDate.now().plusDays(2)
                                 + "\",\"tagNames\":[\"docs\"]}"))
                .andExpect(status().isCreated())
                .andReturn();
        String body = res.getResponse().getContentAsString();
        return Long.parseLong(body.replaceAll("^\\{\"id\":(\\d+).*$", "$1"));
    }

    private Task assignedSeedTask() {
        return taskRepository.findAll().stream()
                .filter(t -> t.getTitle().startsWith("Prepare release notes"))
                .findFirst().orElseThrow();
    }

    // ── pages render ────────────────────────────────────────────────

    @Test
    void publicPagesRender() throws Exception {
        mockMvc.perform(get("/auth/login")).andExpect(status().isOk())
                .andExpect(content().string(containsString("Sign")));
        mockMvc.perform(get("/auth/register")).andExpect(status().isOk());
    }

    @Test
    void userPagesRender() throws Exception {
        Cookie c = demo();
        Long demoId = userRepository.findByEmail("demo@tasknova.com").orElseThrow().getId();
        long id = taskRepository.findByOwnerId(demoId, PageRequest.of(0, 1)).getContent().get(0).getId();

        for (String url : new String[]{
                "/dashboard", "/tasks", "/tasks?status=DONE", "/tasks?q=api&tag=docs&priority=LOW",
                "/tasks?page=1", "/tasks/board", "/tasks/" + id, "/profile"}) {
            mockMvc.perform(get(url).cookie(c))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("demo_user")));
        }
    }

    @Test
    void adminPagesRender() throws Exception {
        Cookie c = admin();
        Long demoId = userRepository.findByEmail("demo@tasknova.com").orElseThrow().getId();
        for (String url : new String[]{
                "/admin", "/admin/users", "/admin/users?search=demo", "/admin/users/" + demoId,
                "/admin/tasks", "/admin/tasks?status=TODO&search=release", "/admin/audit-logs",
                "/admin/audit-logs?actor=demo", "/tasks/" + assignedSeedTask().getId()}) {
            mockMvc.perform(get(url).cookie(c)).andExpect(status().isOk());
        }
    }

    @Test
    void rootRedirectsByRole() throws Exception {
        mockMvc.perform(get("/").cookie(demo())).andExpect(redirectedUrl("/dashboard"));
        mockMvc.perform(get("/").cookie(admin())).andExpect(redirectedUrl("/admin"));
    }

    @Test
    void signedInUserVisitingLoginGoesHome() throws Exception {
        mockMvc.perform(get("/auth/login").cookie(demo())).andExpect(redirectedUrl("/"));
    }

    // ── error handling ──────────────────────────────────────────────

    @Test
    void unknownUrlIs404NotServerError() throws Exception {
        mockMvc.perform(get("/no/such/page").cookie(demo())).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/no-such-endpoint").cookie(demo())).andExpect(status().isNotFound());
    }

    @Test
    void badEnumParamIs400() throws Exception {
        mockMvc.perform(get("/tasks?status=FINISHED").cookie(demo())).andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/tasks?status=FINISHED").cookie(demo()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("status")));
    }

    @Test
    void unauthenticatedApiCallGets401NotRedirect() throws Exception {
        mockMvc.perform(get("/api/tasks")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/dashboard")).andExpect(status().is3xxRedirection());
    }

    @Test
    void validationErrorOnPageRendersErrorPage() throws Exception {
        mockMvc.perform(get("/tasks/abc").cookie(demo()))
                .andExpect(status().isBadRequest())
                .andExpect(content().string(containsString("400")));
    }

    // ── security ────────────────────────────────────────────────────

    @Test
    void authCookieIsHttpOnly() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"demo_user\",\"password\":\"Demo@1234\"}"))
                .andExpect(status().isOk()).andReturn();
        assertThat(res.getResponse().getHeader("Set-Cookie")).contains("HttpOnly");
        assertThat(res.getResponse().getCookie("tasknova_jwt").isHttpOnly()).isTrue();
    }

    @Test
    void logoutRequiresPost() throws Exception {
        mockMvc.perform(get("/auth/logout").cookie(demo())).andExpect(status().isNotFound());
    }

    // ── assignment visibility ───────────────────────────────────────

    @Test
    void assigneeSeesAndCanUpdateButNotDeleteAssignedTask() throws Exception {
        Cookie c = demo();
        long id = assignedSeedTask().getId();

        mockMvc.perform(get("/api/tasks?search=release notes").cookie(c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[*].id").value(hasItem((int) id)));
        mockMvc.perform(get("/api/tasks/" + id).cookie(c)).andExpect(status().isOk());

        mockMvc.perform(patch("/api/tasks/" + id).cookie(c)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"status\":\"IN_PROGRESS\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"));

        mockMvc.perform(delete("/api/tasks/" + id).cookie(c)).andExpect(status().isForbidden());
    }

    @Test
    void strangersCannotSeeOthersTasks() throws Exception {
        String name = "u" + UUID.randomUUID().toString().substring(0, 8);
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"email\":\"" + name + "@x.io\",\"password\":\"Passw0rdA\"}"))
                .andExpect(status().isCreated());
        Cookie stranger = login(name, "Passw0rdA");
        mockMvc.perform(get("/api/tasks/" + assignedSeedTask().getId()).cookie(stranger))
                .andExpect(status().isForbidden());
    }

    // ── task semantics ──────────────────────────────────────────────

    @Test
    void putReplacesAndCanClearDueDateAndTags() throws Exception {
        Cookie c = demo();
        long id = createTask(c, "Temporary for PUT test");

        mockMvc.perform(put("/api/tasks/" + id).cookie(c).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Renamed\",\"dueDate\":null,\"tagNames\":[]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Renamed"))
                .andExpect(jsonPath("$.dueDate").doesNotExist())
                .andExpect(jsonPath("$.tags").isEmpty());

        mockMvc.perform(put("/api/tasks/" + id).cookie(c).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"  \"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void responsesCarryRelativeDueDaysAndDashboardShowsWeek() throws Exception {
        Cookie c = demo();
        long id = createTask(c, "Due-days check");   // created with a due date two days out
        mockMvc.perform(get("/api/tasks/" + id).cookie(c))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueInDays").value(2));
        mockMvc.perform(get("/dashboard").cookie(c))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Today")))
                .andExpect(content().string(containsString("In 2 days")))
                .andExpect(content().string(containsString("class=\"badge")));   // sidebar counters
    }

    @Test
    void unknownTagIsRejected() throws Exception {
        mockMvc.perform(post("/api/tasks").cookie(demo()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"tagNames\":[\"no-such-tag\"]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("no-such-tag")));
    }

    @Test
    void nonAdminCannotAssignViaPatch() throws Exception {
        Cookie c = demo();
        long id = createTask(c, "Temporary for assign test");
        Long adminId = userRepository.findByEmail("admin@tasknova.com").orElseThrow().getId();
        mockMvc.perform(patch("/api/tasks/" + id).cookie(c).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assigneeId\":" + adminId + "}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void tagsEndpointListsVocabulary() throws Exception {
        mockMvc.perform(get("/api/tags").cookie(demo()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].name").value(hasItem("bug")));
    }

    // ── admin guards ────────────────────────────────────────────────

    @Test
    void adminCannotChangeOwnRoleOrDeactivateSelf() throws Exception {
        Cookie c = admin();
        Long adminId = userRepository.findByEmail("admin@tasknova.com").orElseThrow().getId();
        mockMvc.perform(patch("/api/admin/users/" + adminId + "/role").cookie(c)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"USER\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/users/" + adminId + "/toggle-active").cookie(c))
                .andExpect(status().isBadRequest());
    }

    @Test
    void roleChangeValidatesBody() throws Exception {
        Cookie c = admin();
        Long demoId = userRepository.findByEmail("demo@tasknova.com").orElseThrow().getId();
        mockMvc.perform(patch("/api/admin/users/" + demoId + "/role").cookie(c)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(patch("/api/admin/users/" + demoId + "/role").cookie(c)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void membersCannotUseAdminApi() throws Exception {
        mockMvc.perform(get("/api/admin/users").cookie(demo())).andExpect(status().isForbidden());
    }

    // ── registration & profile ──────────────────────────────────────

    @Test
    void registrationIsCaseInsensitiveUnique() throws Exception {
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"ADMIN\",\"email\":\"other@x.io\",\"password\":\"Passw0rdA\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"someone_new\",\"email\":\"DEMO@TaskNova.com\",\"password\":\"Passw0rdA\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void profileUpdateChangesUsernameAndPassword() throws Exception {
        String name = "p" + UUID.randomUUID().toString().substring(0, 8);
        String email = name + "@x.io";
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "\",\"email\":\"" + email + "\",\"password\":\"Passw0rdA\"}"))
                .andExpect(status().isCreated());
        Cookie c = login(email, "Passw0rdA");

        mockMvc.perform(put("/api/users/me").cookie(c).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"demo_user\"}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(put("/api/users/me").cookie(c).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"" + name + "_x\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value(name + "_x"));

        mockMvc.perform(put("/api/users/me").cookie(c).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"Newpass99\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(put("/api/users/me").cookie(c).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"currentPassword\":\"Passw0rdA\",\"newPassword\":\"Newpass99\"}"))
                .andExpect(status().isOk());

        User saved = userRepository.findByEmail(email).orElseThrow();
        assertThat(saved.getUsername()).isEqualTo(name + "_x");
        login(email, "Newpass99");
    }
}
