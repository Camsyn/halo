package run.halo.app.service.impl;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.apache.commons.lang3.SystemUtils;
import org.jetbrains.annotations.NotNull;
import org.kohsuke.github.GHRelease;
import org.kohsuke.github.GHRepository;
import org.kohsuke.github.GitHubBuilder;
import org.kohsuke.github.GitHubRateLimitHandler;
import org.kohsuke.github.connector.GitHubConnectorResponse;
import org.kohsuke.github.extras.okhttp3.OkHttpGitHubConnector;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Service;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import run.halo.app.exception.ServiceException;
import run.halo.app.model.dto.VersionInfoDTO;
import run.halo.app.model.support.HaloConst;
import run.halo.app.service.HaloVersionCtrlService;
import run.halo.app.utils.VmUtils;

/**
 * Halo version control service implementation.
 *
 * @author Chen_Kunqiu
 */
@Service
@Slf4j
public class HaloVersionCtrlServiceImpl implements HaloVersionCtrlService, ApplicationContextAware {

    private GHRepository haloRepo;

    public static final String GITHUB_RELEASES_API =
        "https://api.github.com/repos/halo-dev/halo/releases";
    public static final String GITHUB_RELEASES_LATEST_API =
        "https://api.github.com/repos/halo-dev/halo/releases/latest";
    public static final String GITHUB_RELEASES_TAG_API_BASE =
        "https://api.github.com/repos/halo-dev/halo/releases/tags/";
    /**
     * The dir of local repository for halo jars of diverse versions.
     */
    public static final String REPO_DIR = ".jar";

    /**
     * The directory where user launch the JVM.
     */
    private static final Path USER_DIR = Paths.get(VmUtils.getUserDir());

    /**
     * The directory where current running halo-jar exists.
     */
    private static final Path JAR_DIR = VmUtils.CURR_JAR_DIR;


    private ApplicationContext context;

    @Autowired
    private RestTemplate restTemplate;


    public HaloVersionCtrlServiceImpl(ApplicationContext context,
                                      RestTemplate restTemplate) {
        this.context = context;
        this.restTemplate = restTemplate;
        try {
            haloRepo = connect2github();
        } catch (ServiceException e) {
            haloRepo = null;
        }
    }

    @Override
    public boolean isInLocal(String tagName) {
        final Path dir = JAR_DIR.resolve(REPO_DIR);
        if (!Files.exists(dir)) {
            return false;
        }
        try {
            Path target = Files.list(dir).filter(i ->
                    !StringUtils.hasText(tagName) || i.getFileName().toString().equals(tagName))
                .findFirst().orElse(null);
            if (target == null) {
                return false;
            }
            target =
                Files.list(target).filter(i -> i.toString().endsWith(".jar")).findFirst()
                    .orElse(null);
            if (target == null) {
                return false;
            }
        } catch (IOException e) {
            throw new ServiceException("读取本地目录异常");
        }

        return true;
    }

    @Override
    public List<VersionInfoDTO> getAllReleasesInfo() {

        try {
            if (haloRepo == null) {
                haloRepo = connect2github();
            }
            final List<GHRelease> releases = haloRepo.listReleases().toList();
            return releases.stream().map(data -> {
                final VersionInfoDTO versionInfo = VersionInfoDTO.convertFrom(data);
                versionInfo.setInLocal(isInLocal(data.getTagName()));
                return versionInfo;
            }).collect(Collectors.toList());
        } catch (IOException e) {
            throw new ServiceException("从github api中拉取版本信息失败, url: " + GITHUB_RELEASES_API);
        }

    }

    @Override
    public VersionInfoDTO getReleaseInfoByTag(String tagName) {
        if (!tagName.startsWith("v")) {
            tagName = "v" + tagName;
        }
        try {
            if (haloRepo == null) {
                haloRepo = connect2github();
            }
            final GHRelease release = haloRepo.getReleaseByTagName(tagName);
            final VersionInfoDTO versionInfo = VersionInfoDTO.convertFrom(release);
            versionInfo.setInLocal(isInLocal(tagName));
            return versionInfo;
        } catch (IOException e) {
            String url = GITHUB_RELEASES_TAG_API_BASE + tagName;
            throw new ServiceException("从github api中拉取版本信息失败, url: " + url);
        }
    }

    @Override
    public VersionInfoDTO getLatestReleaseInfo() {
        // final RestTemplate restTemplate = builder.build();
        try {
            if (haloRepo == null) {
                haloRepo = connect2github();
            }
            final GHRelease release = haloRepo.getLatestRelease();
            final VersionInfoDTO versionInfo = VersionInfoDTO.convertFrom(release);
            versionInfo.setInLocal(isInLocal(release.getTagName()));
            return versionInfo;
        } catch (IOException e) {
            throw new ServiceException("从github api中拉取版本信息失败, url: " + GITHUB_RELEASES_LATEST_API);
        }

    }

    @Override
    public void downloadSpecifiedJarToRepo(String tagName) {
        if (!tagName.startsWith("v")) {
            tagName = "v" + tagName;
        }
        if (isInLocal(tagName)) {
            return;
        }
        final Path dstDir = Paths.get(REPO_DIR).resolve(tagName);
        if (!Files.exists(dstDir)) {
            try {
                Files.createDirectories(dstDir);
            } catch (IOException e) {
                throw new ServiceException("创建本地仓库失败, 仓库路径: " + dstDir);
            }
        }
        downloadSpecifiedJar(tagName, dstDir.toString());
    }


    @Override
    public CompletableFuture<String> downloadLatestJar() {
        final VersionInfoDTO info = getLatestReleaseInfo();
        log.info("Downloading the jar with tagName: {}", info.getVersion());
        downloadSpecifiedJarToRepo(info.getVersion());
        return CompletableFuture.completedFuture(info.getVersion());
    }

    @Override
    public void switchVersion(String tagName) {
        if (!tagName.startsWith("v")) {
            tagName = "v" + tagName;
        }
        // if the current version equals to the specified version, then return.
        if (tagName.equals("v" + getCurVersion())) {
            return;
        }
        final Path curJar = VmUtils.CURR_JAR;
        // If not launched in jar, return.
        if (!curJar.toString().endsWith(".jar")) {
            return;
        }
        // If local storage exist specified jar, copy to work dir and get the Path of the copy.
        Path target;
        try {
            if (isInLocal(tagName)) {
                target = copyTargetFromLocal(tagName);
            } else {
                // else, directly download the specified through network into work dir
                //       and get the Path of it.
                target = copyTargetFromRemote(tagName);
            }
            if (target == null) {
                throw new IOException();
            }
        } catch (IOException e) {
            throw new ServiceException("获取目标版本的halo jar包失败, tagName: " + tagName);
        }

        final Path backupTarget = JAR_DIR.resolve("halo-ori-bak.jar");
        log.info("Path of target jar get: {}", target);
        // backup and delete original jar
        try {
            backupAndDeleteSpecifiedJar(curJar, backupTarget);
        } catch (IOException e) {
            throw new ServiceException("备份失败, 备份路径: " + backupTarget);
        }
        log.info("backup finish: {}", backupTarget);

        // launch the new version jar with the same arguments
        startNewVersionApp(target);
        // Close the Spring app
        int exitCode = SpringApplication.exit(context, () -> 0);

        System.exit(exitCode);
    }

    @Override
    public void downloadAndSwitch(String tagName) {
        downloadSpecifiedJarToRepo(tagName);
        switchVersion(tagName);
    }

    @Override
    public void downloadAndSwitchLatest() {
        log.info("latest jar downloading");
        String tagName;
        try {
            tagName = downloadLatestJar().get();
        } catch (InterruptedException | ExecutionException e) {
            throw new ServiceException("下载失败");
        }
        log.info("latest jar download successfully: {}", tagName);
        switchVersion(tagName);
    }

    @Override
    public String getCurVersion() {
        return HaloConst.HALO_VERSION;
    }

    @Override
    public void switchLatest() {
        final String tagName = getLatestReleaseInfo().getVersion();
        switchVersion(tagName);
    }

    @Override
    public void setApplicationContext(@NotNull ApplicationContext applicationContext) {
        context = applicationContext;
    }

    private GHRepository connect2github() {
        try {
            log.info("Connect to Github.");
            final OkHttpClient httpClient =
                new OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
                    .readTimeout(10, TimeUnit.SECONDS).build();
            return new GitHubBuilder()
                .withConnector(new OkHttpGitHubConnector(httpClient))
                .withRateLimitHandler(new GitHubRateLimitHandler() {
                    @Override
                    public void onError(@NotNull GitHubConnectorResponse githubResp)
                        throws IOException {
                        throw new ServiceException("无法访问Github API，可能访问频次过高. URL: "
                            + githubResp.request().url());
                    }
                })
                .build().getRepository("halo-dev/halo");
        } catch (IOException e) {
            throw new ServiceException("无法连接Github，请检查网络.");
        }
    }

    /**
     * Download the specified jar by tagName into the given destination directory.
     *
     * @param tagName the specified tag name of the release
     * @param dstDir  destination directory
     * @return the final path of the downloaded jar
     */
    private String downloadSpecifiedJar(String tagName, String dstDir) {
        final VersionInfoDTO releaseInfo =
            Objects.requireNonNull(getReleaseInfoByTag(tagName));
        final String jarUrl = releaseInfo.getDownloadUrl();
        final String jarName = releaseInfo.getJarName();
        Assert.hasText(jarUrl, "Jar url must not be blank");
        Path target = Paths.get(dstDir).resolve(jarName);
        download(jarUrl, target);
        return target.toString();
    }


    /**
     * Download resource to specified file.
     *
     * <p>As the jar file is very big, it is not appropriate to load it as byte[] in memory,
     * so that directly forwarded it to the file system.
     *
     * @param url     the url to download resource
     * @param tarFile target file
     */
    private void download(String url, Path tarFile) {
        restTemplate.execute(url, HttpMethod.GET, null,
            resp -> {
                log.info("Downloading [{}]", url);
                try (final BufferedInputStream is = new BufferedInputStream(resp.getBody());
                     final BufferedOutputStream os = new BufferedOutputStream(
                         new FileOutputStream(tarFile.toFile()))) {
                    is.transferTo(os);
                } catch (IOException e) {
                    throw new ServiceException("下载失败 "
                        + url
                        + ", 状态码: "
                        + resp.getStatusCode());
                }
                return tarFile;
            });
    }


    /**
     * Copy the target jar file in local repo with specified tagName to work dir
     * and get the Path of the copy.
     *
     * @param tagName version of target jar, such as v1.1, v2.2.1...
     * @return the Path of target jar file
     * @throws IOException exception may happen in copying.
     */
    private Path copyTargetFromLocal(String tagName) throws IOException {
        final Path dir = JAR_DIR.resolve(REPO_DIR);
        Path target =
            Files.list(dir).filter(i ->
                    !StringUtils.hasText(tagName) || i.getFileName().toString().equals(tagName))
                .findFirst().orElse(null);
        if (target == null) {
            return null;
        }
        target =
            Files.list(target).filter(i -> i.toString().endsWith(".jar")).findFirst().orElse(null);
        if (target == null) {
            return null;
        }
        final Path newJar = JAR_DIR.resolve(target.getFileName());
        return Files.copy(target, newJar, StandardCopyOption.REPLACE_EXISTING);
    }


    /**
     * Download the target jar file in github repo with specified tagName to work dir
     * and get the Path of the target jar.
     *
     * @param tagName version of target jar, such as v1.1, v2.2.1...
     * @return the Path of target jar file
     */
    private Path copyTargetFromRemote(String tagName) {
        return Paths.get(downloadSpecifiedJar(tagName, JAR_DIR.toString()));
    }


    /**
     * Backup and delete the specified jar file.
     * The implementation in Windows differs as the file lock in Windows.
     *
     * @param curJar       the specified jar path
     * @param backupTarget the path of the backup
     * @throws IOException the exception may arise in the process of backup
     */
    private void backupAndDeleteSpecifiedJar(Path curJar, Path backupTarget) throws IOException {
        final Path backupDir = backupTarget.getParent();
        assert backupDir != null;
        Files.createDirectories(backupDir);
        Files.copy(curJar, backupTarget, StandardCopyOption.REPLACE_EXISTING);
        ProcessBuilder pb = new ProcessBuilder();
        pb.directory(USER_DIR.toFile());
        if (SystemUtils.IS_OS_WINDOWS) {
            pb.command("cmd", "/c",
                "ping localhost -n 10 > nul && del " + curJar.toAbsolutePath());
        } else if (SystemUtils.IS_OS_LINUX) {
            /*
             * On Unix-like operating systems, there is no file locking,
             * thus you can directly change the name of the current JAR.
             * If you do so, however, `SpringApplication.exit` will not
             * terminate the program properly
             * */
            pb.command("sh", "-c", "sleep 10s && rm -f " + curJar.toAbsolutePath());
        } else if (SystemUtils.IS_OS_MAC) {
            /*
             * On Unix-like operating systems, there is no file locking,
             * thus you can directly change the name of the current JAR.
             * If you do so, however, `SpringApplication.exit` will not
             * terminate the program properly
             * */
            pb.command("sh", "-c", "sleep 10s && rm -f " + curJar.toAbsolutePath());
        } else {
            pb = null;
        }
        if (pb != null) {
            ProcessBuilder finalPb = pb;
            Runtime.getRuntime().addShutdownHook(new Thread(
                () -> {
                    try {
                        finalPb.start();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                }
            ));
        }

    }

    /**
     * Start the selected new version application of halo by launch another process.
     *
     * @param target the target jar file of new version app
     */
    private void startNewVersionApp(Path target) {
        final List<String> cmd = VmUtils.getNewLaunchCommand(target.toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(USER_DIR.toFile());
        log.info("Cmd to launch new version halo app: {}", String.join(" ", cmd));
        // Registers a new virtual-machine shutdown hook to launch new version halo app.
        // At the time of JVM exiting, the network resource it owning would be released,
        // hence, starting new halo-app at that moment could avoid port conflict.
        Runtime.getRuntime().addShutdownHook(new Thread(
            () -> {
                try {
                    final Process process = pb.inheritIO().start();
                    System.out.println(
                        "\n------------------------------\n"
                            + "New process PID: " + process.pid() + "\n"
                            + "Command to launch: " + String.join(" ", cmd)
                            + "\n------------------------------\n");
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        ));

    }


}
