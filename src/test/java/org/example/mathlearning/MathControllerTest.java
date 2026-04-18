package org.example.mathlearning;



import org.example.mathlearning.model.User;
import org.example.mathlearning.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
public class MathControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    private MockHttpSession session;
    private User testUser;

    @BeforeEach
    void setUp() {
        userRepository.deleteAll();

        testUser = new User();
        testUser.setUsername("testuser");
        testUser.setEmail("test@example.com");
        testUser.setPassword("pass123");
        testUser.setCurrentLevel(1);
        userRepository.save(testUser);

        session = new MockHttpSession();
        session.setAttribute("userId", testUser.getId());
        session.setAttribute("username", testUser.getUsername());
        session.setAttribute("userLevel", 1);
    }

    @Test
    void shouldRedirectToLoginWhenNotAuthenticated() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login"));
    }

    @Test
    void shouldAccessIndexWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/").session(session))
                .andExpect(status().isOk())
                .andExpect(view().name("index"));
    }

    @Test
    void shouldAccessLearnPageWhenAuthenticated() throws Exception {
        mockMvc.perform(get("/learn")
                        .session(session)
                        .param("topic", "algebra")
                        .param("level", "1"))
                .andExpect(status().isOk())
                .andExpect(view().name("learn"))
                .andExpect(model().attributeExists("topic", "level"));
    }

    @Test
    void shouldResetProgress() throws Exception {
        mockMvc.perform(get("/reset").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/profile"));
    }
}