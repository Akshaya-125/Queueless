package com.queueless.ui;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.openqa.selenium.By;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.Select;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end UI tests. Prerequisite: the application is running (docker compose up) and the
 * environment variables BASE_URL (default http://localhost:8080) and ADMIN_PASSWORD are set.
 * Each test creates its own user and its own service counter, so tests do not depend on each other.
 */
class QueueLessUiTest extends BaseUiTest {

    private static String adminToken;

    @BeforeAll
    static void connectToApi() {
        adminToken = TestApi.adminToken();
    }

    // ---------------------------------------------------------------- authentication

    @Test
    void loginWithInvalidCredentialsShowsError() {
        loginThroughUi("nobody-" + TestApi.unique("x") + "@test.local", "WrongPassword1");
        waitForText(By.id("form-error"), "Invalid email or password");
        assertTrue(driver.getCurrentUrl().contains("login.html"));
    }

    @Test
    void registrationShowsValidationErrors() {
        open("register.html");
        type(By.id("fullName"), "Test Person");
        type(By.id("email"), "not-an-email");
        type(By.id("password"), "short");
        click(By.id("register-btn"));
        waitForText(By.id("err-email"), "valid email");
        waitForText(By.id("err-password"), "at least 8");
    }

    @Test
    void loginWithValidCredentialsOpensDashboard() {
        TestApi.Account user = TestApi.registerUser();
        loginThroughUi(user.email(), TestApi.USER_PASSWORD);
        wait.until(ExpectedConditions.urlContains("dashboard.html"));
        assertEquals("Dashboard", visible(By.id("dashboard-title")).getText());
        visible(By.id("services"));
    }

    @Test
    void userCannotOpenAdminPage() {
        TestApi.Account user = TestApi.registerUser();
        loginThroughUi(user.email(), TestApi.USER_PASSWORD);
        wait.until(ExpectedConditions.urlContains("dashboard.html"));
        open("admin.html");
        wait.until(ExpectedConditions.urlContains("dashboard.html"));
    }

    // ---------------------------------------------------------------- user workflow

    @Test
    void userBooksTokenAndSeesNumberAndPosition() {
        long serviceId = TestApi.createService(adminToken);
        TestApi.Account user = TestApi.registerUser();
        loginThroughUi(user.email(), TestApi.USER_PASSWORD);
        wait.until(ExpectedConditions.urlContains("dashboard.html"));

        open("book.html");
        By option = By.cssSelector("#service-select option[value='" + serviceId + "']");
        visible(By.id("service-select"));
        wait.until(ExpectedConditions.presenceOfElementLocated(option));
        new Select(driver.findElement(By.id("service-select"))).selectByValue(String.valueOf(serviceId));
        wait.until(ExpectedConditions.elementToBeClickable(By.id("book-btn")));
        click(By.id("book-btn"));

        waitForText(By.id("result-number"), "1");            // first token of a brand-new service today
        assertEquals("1", visible(By.id("result-position")).getText());

        click(By.id("result-track"));
        wait.until(ExpectedConditions.urlContains("track.html"));
        waitForText(By.id("token-status"), "WAITING");
        assertEquals("1", visible(By.id("token-number")).getText());
        assertEquals("1", visible(By.id("token-position")).getText());
    }

    @Test
    void trackingPageShowsStatusUpdateWhenAdminCallsToken() {
        long serviceId = TestApi.createService(adminToken);
        TestApi.Account user = TestApi.registerUser();
        long tokenId = TestApi.book(user.token(), serviceId).get("id").asLong();

        loginThroughUi(user.email(), TestApi.USER_PASSWORD);
        wait.until(ExpectedConditions.urlContains("dashboard.html"));
        open("track.html?id=" + tokenId);
        waitForText(By.id("token-status"), "WAITING");

        TestApi.call("POST", "/admin/services/" + serviceId + "/call-next", adminToken, null);

        // the page refreshes itself every 5 seconds
        new WebDriverWait(driver, Duration.ofSeconds(25))
                .until(ExpectedConditions.textToBePresentInElementLocated(By.id("token-status"), "CALLED"));
    }

    @Test
    void userCancelsWaitingToken() {
        long serviceId = TestApi.createService(adminToken);
        TestApi.Account user = TestApi.registerUser();
        long tokenId = TestApi.book(user.token(), serviceId).get("id").asLong();

        loginThroughUi(user.email(), TestApi.USER_PASSWORD);
        wait.until(ExpectedConditions.urlContains("dashboard.html"));
        open("track.html?id=" + tokenId);
        waitForText(By.id("token-status"), "WAITING");
        click(By.id("cancel-btn"));
        waitForText(By.id("token-status"), "CANCELLED");

        assertEquals("CANCELLED", TestApi.getToken(user.token(), tokenId).get("status").asText());
    }

    // ---------------------------------------------------------------- administrator workflow

    @Test
    void adminCallsNextServesAndCompletesToken() {
        long serviceId = TestApi.createService(adminToken);
        TestApi.Account user = TestApi.registerUser();
        long tokenId = TestApi.book(user.token(), serviceId).get("id").asLong();

        loginThroughUi(TestApi.ADMIN_EMAIL, TestApi.ADMIN_PASSWORD);
        wait.until(ExpectedConditions.urlContains("admin.html"));

        By status = By.cssSelector("[data-testid='status-" + tokenId + "']");
        waitForText(status, "WAITING");

        click(By.cssSelector("[data-testid='call-next-" + serviceId + "']"));
        waitForText(status, "CALLED");

        click(By.cssSelector("[data-testid='serve-" + tokenId + "']"));
        waitForText(status, "SERVING");

        click(By.cssSelector("[data-testid='complete-" + tokenId + "']"));
        waitForText(status, "COMPLETED");

        assertEquals("COMPLETED", TestApi.getToken(user.token(), tokenId).get("status").asText());
    }

    @Test
    void adminSeesErrorWhenNoTokenIsWaiting() {
        long serviceId = TestApi.createService(adminToken);

        loginThroughUi(TestApi.ADMIN_EMAIL, TestApi.ADMIN_PASSWORD);
        wait.until(ExpectedConditions.urlContains("admin.html"));
        click(By.cssSelector("[data-testid='call-next-" + serviceId + "']"));
        waitForText(By.cssSelector(".toast.error"), "No tokens are waiting");
    }

    @Test
    void adminCanAddServiceCounter() {
        loginThroughUi(TestApi.ADMIN_EMAIL, TestApi.ADMIN_PASSWORD);
        wait.until(ExpectedConditions.urlContains("admin.html"));
        String name = TestApi.unique("UI-Added");
        type(By.id("svc-name"), name);
        type(By.id("svc-location"), "Selenium lab");
        click(By.id("service-save"));
        waitForText(By.id("services-body"), name);
    }
}
