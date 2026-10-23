package com.queueless.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import java.time.Duration;

abstract class BaseUiTest {

    protected WebDriver driver;
    protected WebDriverWait wait;

    @BeforeEach
    void startBrowser() {
        driver = newDriver();
        wait = new WebDriverWait(driver, Duration.ofSeconds(15));
    }

    @AfterEach
    void stopBrowser() {
        if (driver != null) driver.quit();
    }

    /** Selenium Manager (built into Selenium 4) downloads the matching ChromeDriver automatically. */
    protected static WebDriver newDriver() {
        ChromeOptions options = new ChromeOptions();
        if (!"false".equalsIgnoreCase(TestApi.env("HEADLESS", "true"))) {
            options.addArguments("--headless=new");
        }
        options.addArguments("--window-size=1366,900", "--no-sandbox", "--disable-dev-shm-usage");
        return new ChromeDriver(options);
    }

    // ------------------------------------------------------------ helpers

    protected void open(String page) {
        driver.get(TestApi.BASE_URL + "/" + page);
    }

    protected WebElement visible(By by) {
        return wait.until(ExpectedConditions.visibilityOfElementLocated(by));
    }

    protected void type(By by, String text) {
        WebElement el = visible(by);
        el.clear();
        el.sendKeys(text);
    }

    /** Click that survives the admin page re-rendering its tables (auto refresh). */
    protected void click(By by) {
        wait.ignoring(StaleElementReferenceException.class).until(d -> {
            d.findElement(by).click();
            return true;
        });
    }

    protected void waitForText(By by, String expected) {
        wait.ignoring(StaleElementReferenceException.class)
                .until(ExpectedConditions.textToBePresentInElementLocated(by, expected));
    }

    protected void loginThroughUi(String email, String password) {
        open("login.html");
        type(By.id("email"), email);
        type(By.id("password"), password);
        click(By.id("login-btn"));
    }
}
