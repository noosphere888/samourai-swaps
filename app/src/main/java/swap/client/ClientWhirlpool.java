package swap.client;

import com.samourai.whirlpool.cli.beans.CliResult;
import com.samourai.whirlpool.cli.config.CliConfig;
import com.samourai.whirlpool.cli.services.CliConfigService;
import com.samourai.whirlpool.cli.services.WSMessageService;
import com.samourai.whirlpool.cli.services.WsNotifierService;
import com.samourai.whirlpool.cli.utils.CliUtils;
import com.samourai.whirlpool.protocol.WhirlpoolProtocol;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.core.env.Environment;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;
import swap.gui.GUISwap;
import swap.helper.HelperProperties;
import swap.lib.AppSwap;
import swap.whirlpool.ServiceWhirlpool;

import javax.annotation.PreDestroy;
import java.io.File;
import java.io.IOException;
import java.lang.invoke.MethodHandles;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;

@SpringBootApplication
@EnableCaching
@ComponentScan(value = {
        "swap",
        "com.samourai.whirlpool.cli.config",
        "com.samourai.whirlpool.cli.services"
}, excludeFilters = {
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = WsNotifierService.class),
        @ComponentScan.Filter(type = FilterType.ASSIGNABLE_TYPE, value = WSMessageService.class),
})
@EntityScan(value = "com.samourai.whirlpool.cli.persistence")
@EnableJpaRepositories(value = "com.samourai.whirlpool.cli.persistence")
@EnableScheduling
@EnableConfigurationProperties(value = {
        CliConfig.class
})
public class ClientWhirlpool implements ApplicationRunner {
    public static ConfigurableApplicationContext applicationContext;
    public static AtomicBoolean walletOpened = new AtomicBoolean(false);
    public static String passphrase;
    private static ClientWhirlpool instance;
    private static FileLock fileLock;
    private static boolean restart;
    private static Integer exitCode;
    private final Logger log = LoggerFactory.getLogger(MethodHandles.lookup().lookupClass());
    @Autowired
    private Environment env;
    @Autowired
    private ServiceWhirlpool serviceWhirlpool;

    public static ClientWhirlpool getInstance() {
        return instance;
    }

    public static void copyWhirlpoolClientFilesToTempLocation() {
        File propertiesFile = HelperProperties.getPropertiesFile();
        try {
            Files.copy(propertiesFile.toPath(), new File(CliConfigService.CLI_CONFIG_FILENAME).toPath(), StandardCopyOption.REPLACE_EXISTING);

            for (File file : Objects.requireNonNull(AppSwap.getSwapRootDir().listFiles())) {
                if (file.isFile() && file.getName().startsWith("whirlpool-cli-db-")) {
                    Files.copy(file.toPath(), new File(file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void handleTempWhirlpoolClientFiles() {
        new File(CliConfigService.CLI_CONFIG_FILENAME).delete();
        for (File file : Objects.requireNonNull(new File(".").listFiles())) {
            if (file.isFile() && file.getName().startsWith("whirlpool-cli-db-")) {
                try {
                    Files.move(file.toPath(), new File(AppSwap.getSwapRootDir(), file.getName()).toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
                    file.delete();
                } catch (IOException ignored) {
                }
            }
        }
    }

    public static Integer getExitCode() {
        return exitCode;
    }

    public static boolean getRestart() {
        return restart;
    }

    public static void setRestart(boolean r) {
        restart = r;
    }

    @PreDestroy
    public void preDestroy() {
        log.info("PRE-DESTROY");
        // unlock directory
        if (fileLock != null && fileLock.acquiredBy().isOpen()) {
            try {
                serviceWhirlpool.unlockDirectory(fileLock);
            } catch (Exception ignored) {
            }
        }

        try {
            // shutdown
            serviceWhirlpool.shutdown();
        } catch (Exception ignored) {
        }
    }

    @Override
    public void run(ApplicationArguments applicationArguments) throws Exception {
        instance = this;
        CliUtils.setLogLevel(false, false); // run twice to fix incorrect log level

        if (log.isDebugEnabled()) {
            log.debug("Run... " + Arrays.toString(applicationArguments.getSourceArgs()));
        }
        if (log.isDebugEnabled()) {
            log.debug("[cli/debug] debug=" + false + ", debugClient=" + false);
            log.debug("[cli/protocolVersion] " + WhirlpoolProtocol.PROTOCOL_VERSION);
        }

        try {
            if (fileLock != null && fileLock.acquiredBy().isOpen()) {
                serviceWhirlpool.unlockDirectory(fileLock);
                serviceWhirlpool.shutdown();
            }

            serviceWhirlpool.setup();
            fileLock = serviceWhirlpool.lockDirectory();

            CliResult cliResult = serviceWhirlpool.run(false, passphrase);
            switch (cliResult) {
                case RESTART:
                    restart = true;
                    break;
                case EXIT_SUCCESS:
                    exitCode = 0;
                    break;
                case KEEP_RUNNING:
                    break;
            }
        } catch (Exception e) {
            log.error("WHIRLPOOL EXCEPTION", e);
            if ((e.getMessage().contains("Unable to connect to wallet backend") || e.getMessage().contains("SOCKS4") || e.getMessage().contains("Total timeout 30000 ms elapsed")) && !ClientWhirlpool.walletOpened.get()) {
                restart = true;
            } else {
                exitCode = 1;
            }
        }
    }

    public void stop() {
        preDestroy();
        if (applicationContext != null) {
            log.info("Closing...");
            applicationContext.close();
        }

        if (!GUISwap.running.get()) {
            handleTempWhirlpoolClientFiles();
        }
    }

    public ServiceWhirlpool getWhirlpoolService() {
        return serviceWhirlpool;
    }
}
