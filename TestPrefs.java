package helpers;

import io.appium.java_client.AppiumDriver;
import io.appium.java_client.InteractsWithApps;
import io.appium.java_client.MobileElement;
import io.appium.java_client.android.AndroidDriver;
import io.appium.java_client.ios.IOSDriver;
import io.appium.java_client.pagefactory.AppiumFieldDecorator;
import io.appium.java_client.service.local.AppiumDriverLocalService;
import io.appium.java_client.service.local.AppiumServiceBuilder;
import io.appium.java_client.service.local.flags.GeneralServerFlag;
import org.openqa.selenium.NoSuchElementException;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.remote.DesiredCapabilities;
import org.openqa.selenium.support.PageFactory;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.FluentWait;
import org.openqa.selenium.support.ui.Wait;
import org.testng.annotations.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.URL;
import java.time.Duration;
import java.util.HashMap;
import java.util.Properties;

public class TestPrefs {
    protected static ThreadLocal<AppiumDriver> driver = new ThreadLocal<AppiumDriver>();
    protected static ThreadLocal<Properties> props = new ThreadLocal<Properties>();
    protected static ThreadLocal<HashMap<String, String>> strings = new ThreadLocal<HashMap<String, String>>();
    protected static ThreadLocal<String> platform = new ThreadLocal<String>();
    protected static ThreadLocal<String> dateTime = new ThreadLocal<String>();
    protected static ThreadLocal<String> deviceName = new ThreadLocal<String>();
    private static AppiumDriverLocalService server;

    @BeforeSuite
    public void beforeSuite() throws Exception {
        //ThreadContext.put("ROUTINGKEY", "ServerLogs");
        server = getAppiumService();
        if (!checkIfAppiumServerIsRunnning(4723)) {
            server.start();
            server.clearOutPutStreams();
            //utils.log().info("Appium server started");
        } else {
            //utils.log().info("Appium server already running");
        }
    }
    //////////////////////////////////

    @Parameters({"emulator", "platformName", "udid", "deviceName", "systemPort",
            "chromeDriverPort", "wdaLocalPort", "webkitDebugProxyPort"})
    @BeforeTest
    public void emuRun(@Optional("androidOnly") String emulator,
                       String platformName,
                       String udid,
                       String deviceName,
                       @Optional("androidOnly") String systemPort,
                       @Optional("androidOnly") String chromeDriverPort,
                       @Optional("iOSOnly") String wdaLocalPort,
                       @Optional("iOSOnly") String webkitDebugProxyPort) throws Exception {
        //Set all the provided parameters
        //setDateTime(utils.dateTime());
        setPlatform(platformName);
        setDeviceName(deviceName);
        URL url;
        InputStream inputStream = null;
        InputStream stringsis = null;
        Properties properties = new Properties();
        AppiumDriver driver;

        //Create log file in log dir (and create it if there isn't)
        String strFile = "logs" + File.separator + platformName + "_" + deviceName;
        File logFile = new File(strFile);
        if (!logFile.exists()) {
            logFile.mkdirs();
        }
        //Route logs to separate file for each thread
        //ThreadContext.put("ROUTINGKEY", strFile);
        //utils.log().info("log path: " + strFile);

        try {
            properties = new Properties();
            String propFileName = "config.properties";
            String xmlFileName = "strings/strings.xml";

            //utils.log().info("load properties " + propFileName);
            inputStream = getClass().getClassLoader().getResourceAsStream(propFileName);
            properties.load(inputStream);
            setProps(properties);

            /*utils.log().info("load " + xmlFileName);
            stringsis = getClass().getClassLoader().getResourceAsStream(xmlFileName);
            setStrings(utils.parseStringXML(stringsis));*/

            //Set platform independent desired capabilities for Appium
            DesiredCapabilities desiredCapabilities = new DesiredCapabilities();
            desiredCapabilities.setCapability("platformName", platformName);
            desiredCapabilities.setCapability("deviceName", deviceName);
            desiredCapabilities.setCapability("udid", udid);
            url = new URL(properties.getProperty("appiumURL"));

            //Set platform depended desired capabilities for Appium
            File androidApp = new File("src/test/resources/apks/2020_09_02_13_38_open-broker-Apptt_Release-2.20.0(27).apk");
            switch (platformName) {
                //For Android
                case "Android":
                    desiredCapabilities.setCapability("automationName", properties.getProperty("androidAutomationName"));
                    desiredCapabilities.setCapability("appPackage", properties.getProperty("androidAppPackage"));
                    desiredCapabilities.setCapability("appActivity", properties.getProperty("androidAppActivity"));
                    if (emulator.equalsIgnoreCase("true")) {
                        desiredCapabilities.setCapability("avd", deviceName);
                        desiredCapabilities.setCapability("avdLaunchTimeout", 120000);
                    }
                    desiredCapabilities.setCapability("systemPort", systemPort);
                    desiredCapabilities.setCapability("chromeDriverPort", chromeDriverPort);
                    String androidAppUrl = androidApp.getAbsolutePath();
                    //utils.log().info("Android, appUrl is" + androidAppUrl);
                    desiredCapabilities.setCapability("app", androidAppUrl);
                    desiredCapabilities.setCapability("noReset", true);
                    driver = new AndroidDriver(url, desiredCapabilities);
                    break;
                //For iOS
                case "iOS":
                    desiredCapabilities.setCapability("automationName", properties.getProperty("iOSAutomationName"));
                    //String iOSAppUrl = getClass().getResource(properties.getProperty("iOSAppLocation")).getFile();
                    //utils.log().info("iOS, appUrl is" + iOSAppUrl);
                    desiredCapabilities.setCapability("bundleId", properties.getProperty("iOSBundleId"));
                    desiredCapabilities.setCapability("wdaLocalPort", wdaLocalPort);
                    desiredCapabilities.setCapability("webkitDebugProxyPort", webkitDebugProxyPort);
                    //desiredCapabilities.setCapability("app", iOSAppUrl);
                    driver = new IOSDriver(url, desiredCapabilities);
                    break;
                default:
                    throw new Exception("Invalid platform! - " + platformName);
            }
            setDriver(driver);
            //utils.log().info("driver initialized: " + driver);
        } catch (Exception e) {
            //utils.log().fatal("driver initialization failure. ABORT!!!\n" + e.toString());
            throw e;
        } finally {
            if (inputStream != null) {
                inputStream.close();
            }
            if (stringsis != null) {
                stringsis.close();
            }
        }

    }

    @AfterSuite
    public void afterSuite() {
        server.stop();
        //utils.log().info("Appium server stopped");
    }

    @AfterMethod
    public void afterMethod() throws InterruptedException {
        Thread.sleep(6000);
    }

    //////////////////////////////////
    //Method that returns new Appium Service
    public AppiumDriverLocalService getAppiumService() {
        //Create an environment that consists of:
        //  - PATH variable:
        //      - JAVA_HOME;
        //      - Android SDK Tools ($ANDROID_HOME/tools);
        //      - Android SDK Platform tools ($ANDROID_HOME/platform-tools);
        //      - OS $PATH variable (echo $PATH in terminal);
        //  - ANDROID_HOME variable.
        HashMap<String, String> environment = new HashMap<String, String>();

        //Initialize all needed variables by System.getenv() or manually
        String androidHomePath = "/Users/anastasiarozanova/Library/Android/sdk";
        String javaJdkHomeBinPath = System.getenv("JAVA_HOME");
        String androidSdkToolsPath = "/Users/anastasiarozanova/Library/Android/sdk/tools";
        String androidSdkPlatformToolsPath = "/Users/anastasiarozanova/Library/Android/sdk/platform-tools";

        //Add them to environment HashMap (2 elements):
        // PATH
        environment.put("PATH", javaJdkHomeBinPath + ":"
                + androidSdkToolsPath + ":"
                + androidSdkPlatformToolsPath + ":"
                + System.getenv("PATH"));

        // ANDROID_HOME
        environment.put("ANDROID_HOME", androidHomePath);

        //To return AppiumService it must be built first, for that we need:
        // 1. NodeJS installed and its path
        String nodeJsPath = "/usr/local/Cellar/node/14.3.0/bin/node";

        // 2. Provide AppiumJS itself
        String appiumMainJs = "/usr/local/Cellar/appium/1.17.1/libexec/lib/node_modules/appium/build/lib/main.js";

        //Then we can build and return Appium Service
        return AppiumDriverLocalService.buildService(new AppiumServiceBuilder()
                .usingDriverExecutable(new File(nodeJsPath)) //using NodeJS
                .withAppiumJS(new File(appiumMainJs)) //and AppiumJS
                .usingPort(4723) // using the specific port
                .withArgument(GeneralServerFlag.SESSION_OVERRIDE) //idk wtf is this
                .withEnvironment(environment) //using our environment
                .withLogFile(new File("ServerLogs/server.log"))); //and logging to server.log

    }
    //////////////////////////////
    public boolean checkIfAppiumServerIsRunnning(int port) throws Exception {
        boolean isAppiumServerRunning = false;
        ServerSocket socket;
        try {
            socket = new ServerSocket(port);
            socket.close();
        } catch (IOException e) {
            System.out.println("1");
            isAppiumServerRunning = true;
        } finally {
            socket = null;
        }
        return isAppiumServerRunning;
    }

    //Gettets
    public String getPlatform() {
        return platform.get();
    }
    public String getDateTime() {
        return dateTime.get();
    }
    public String getDeviceName() {
        return deviceName.get();
    }
    public Properties getProps() {
        return props.get();
    }
    public HashMap<String, String> getStrings() {
        return strings.get();
    }
    public AppiumDriver getDriver() {
        return driver.get();
    }

    //Setters
    public void setPlatform(String platform2) {
        platform.set(platform2);
    }
    public void setDateTime(String dateTime2) {
        dateTime.set(dateTime2);
    }
    public void setDeviceName(String deviceName2) {
        deviceName.set(deviceName2);
    }
    public void setProps(Properties props2) {
        props.set(props2);
    }
    public void setStrings(HashMap<String, String> strings2) {
        strings.set(strings2);
    }
    public void setDriver(AppiumDriver driver2) {
        driver.set(driver2);
    }

    ////////////////////////////////
    public void closeApp() { ((InteractsWithApps) getDriver()).closeApp(); }
    public void launchApp() {
        ((InteractsWithApps) getDriver()).launchApp();
    }
    ////////////////////////////////

    public void click(MobileElement e) {
        waitForVisibility(e);
        e.click();
    }

    public void waitForVisibility(WebElement e) {
        Wait<AppiumDriver> wait = new FluentWait<AppiumDriver>(getDriver())
                .withTimeout(Duration.ofSeconds(30))
                .pollingEvery(Duration.ofSeconds(3))
                .ignoring(NoSuchElementException.class);
        wait.until(ExpectedConditions.visibilityOf(e));
    }

    public void waitForVisibility(WebElement e, Duration d) {
        Wait<AppiumDriver> wait = new FluentWait<AppiumDriver>(getDriver())
                .withTimeout(Duration.ofSeconds(3))
                .ignoring(NoSuchElementException.class);
        wait.until(ExpectedConditions.visibilityOf(e));
    }

    public void setValue(MobileElement e, String value){
        waitForVisibility(e);
        e.setValue(value);
    }

    public boolean isExists(WebElement e){
        try {
            e.isDisplayed();
            return true;
        } catch (NoSuchElementException ex) {
            return false;
        }
    }

}
