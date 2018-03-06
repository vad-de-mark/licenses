package com;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import one.util.streamex.StreamEx;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class Main {

    public static void main(String[] args) throws Throwable {
        final Path path = Paths.get("C:","Users", "Vadim_Markov", "Documents", "tri-adv", "lib", "downloaded");
//        final Map<String, String> collect =
//                Files.walk(path)
//                .filter(p -> p.toString().endsWith(".jar"))
////                .map(Path::getFileName)
//                .collect(Collectors.groupingBy(
//                        Main::extractJarInfo, Collectors.mapping(Main::getLicense, Collectors.joining(","))
//                ));

        final Map<String, Map<ZipEntry, Optional<String>>> map = Files.walk(path)
                .filter(p -> p.toString().endsWith(".jar"))
                .filter(p -> !p.getFileName().toString().startsWith("nr-"))
                .filter(p -> !p.getFileName().toString().startsWith("nrnewsml"))
                .filter(p -> !p.getFileName().toString().startsWith("javax"))
                .map(Main::createZip)
                .collect(Collectors.toMap(Main::extractJarInfo, Main::getLicenseCandidates));
        final Map<Boolean, Map<String, Map<ZipEntry, Optional<String>>>> mapMapMap
                = map.entrySet().stream().collect(Collectors.partitioningBy(e -> !e.getValue().isEmpty(), toMap(Map.Entry::getKey, Map.Entry::getValue)));
        mapMapMap.get(true)
                .forEach((key, value) -> {
                    System.out.println(key + "; Apache;");
//                    value.forEach((k, v) -> {
//                        if (!v.get().isEmpty())
//                        System.out.print("; " + v.map(Object::toString).orElse("NONE"));
//                    });
//                    System.out.println(";");
                });
        Stream.iterate(0, i -> ++i).limit(5).forEach(i -> System.out.println());
        mapMapMap.get(false)
        .forEach((key, value) -> {
                    System.out.println(key);
                    value.forEach((k, v) -> {
                        System.out.println("\t" + k + ": " + v.map(Object::toString).orElse("NONE"));
                    });
                });


        System.out.println("mapMapMap.get(true).size() = " + mapMapMap.get(true).size());
        System.out.println("mapMapMap.get(false).size() = " + mapMapMap.get(false).size());



//        for (Map.Entry<String, String> entry : collect.entrySet()) {
////            System.out.println(entry.getKey() + ": " + entry.getValue());
//        }
//        collect.values().removeIf(s -> s == null || s.equalsIgnoreCase(""));
//        System.out.println(collect.size());
    }

    private static String extractJarInfo(ZipFile zipFile) {
        final String fileName = zipFile.getName().replaceAll("\\.jar$", "").replaceAll("^.*\\\\", "");
        final String[] split = fileName.split("-");
        final String libName = StreamEx.of(Arrays.stream(split)).takeWhile(Main::isVersionOrigin).collect(Collectors.joining("-"));
        final String libVersion = StreamEx.of(Arrays.stream(split)).dropWhile(Main::isVersionOrigin).collect(Collectors.joining("-"));
        return String.format("%s; %s", libName, libVersion);
    }

    private static boolean isVersionOrigin(String str) {
        return !Pattern.matches("[\\.0-9]+(?:\\.(?:RELEASE|Final))?", str);
    }

    private static String getLicense(Path path) {
        return Stream.of(createZip(path))
                .filter(Main::containsLicense)
                .map(Main::getLicenseInputStream)
                .map(Optional::get)
                .map(InputStreamReader::new)
                .map(BufferedReader::new)
                .flatMap(BufferedReader::lines)
//                .filter(Main::withLicenseName)
                .filter(getLicensed_under())
                .findFirst().orElse("");
    }

    private static Predicate<String> getLicensed_under() {
        return s -> s.toLowerCase().contains("apache (software) license") || s.toLowerCase().contains("licensed under")
                || s.toLowerCase().contains("http://www.apache.org/licenses/")
                || s.toLowerCase().contains("cddl")
                ;
    }

    private static Optional<String> getLicense(ZipFile zipFile, ZipEntry zipEntry) {
        try {
            return Optional.ofNullable(new BufferedReader(new InputStreamReader(zipFile.getInputStream(zipEntry)))
                    .lines()
                    .filter(getLicensed_under())
                    .collect(Collectors.joining(" "))
                    .replace(";", ".")
            );
        }
        catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Map<ZipEntry, Optional<String>> getLicenseCandidates(final ZipFile zipFile) {
        return Stream.of(zipFile)
                .filter(Main::containsLicense)
                .flatMap(ZipFile::stream)
                .filter(Main::isLicense)
                .filter(ze -> !ze.isDirectory())
                .filter(exclude("java", "xml", "class", "html"))
                .collect(toMap(Function.identity(), ze -> getLicense(zipFile, ze)));
    }

    private static Predicate<ZipEntry> exclude(final String ... formats) {
        return ze -> Arrays.stream(formats).map(s->"."+s).map(String::toLowerCase).noneMatch(s -> ze.getName().endsWith(s));
    }

    private static boolean withLicenseName(String s) {
        final String lowerCase = s.toLowerCase();
        return lowerCase.contains("apache")
                || lowerCase.contains("mit")
                || lowerCase.contains("gpl")
                || lowerCase.contains("gnu");
    }

    private static Optional<? extends InputStream> getLicenseInputStream(ZipFile zipFile) {
            return  retrieveLicense(zipFile).map((ZipEntry ze) -> {
                try {
                    return zipFile.getInputStream(ze);
                }
                catch (Exception e) {
                    throw new RuntimeException(e);
                }
            });
    }

    private static Optional<? extends ZipEntry> retrieveLicense(final ZipFile zipFile) {
        return zipFile.stream().filter(Main::isLicense).findFirst();
    }

    private static boolean containsLicense(final ZipFile zipFile) {
        return zipFile.stream()
                .anyMatch(Main::isLicense);
    }

    private static boolean isLicense(final ZipEntry ze) {
        return ze.getName().toLowerCase().contains("license");
    }

    private static ZipFile createZip(final Path path) {
        try {
            return new ZipFile(path.toFile());
        }
        catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
