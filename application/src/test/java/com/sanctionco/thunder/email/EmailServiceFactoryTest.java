package com.sanctionco.thunder.email;

import com.google.common.io.Resources;
import com.sanctionco.thunder.TestResources;
import com.sanctionco.thunder.email.disabled.DisabledEmailServiceFactory;
import com.sanctionco.thunder.email.ses.SesEmailServiceFactory;

import io.dropwizard.jackson.DiscoverableSubtypeResolver;

import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mockStatic;

class EmailServiceFactoryTest {
  private static EmailServiceFactory DEFAULT_FACTORY;

  private static final String DEFAULT_SUCCESS_HTML_RESOURCE_FILE = "success.html";
  private static final String DEFAULT_BODY_HTML_RESOURCE_FILE = "verification.html";
  private static final String DEFAULT_BODY_TEXT_RESOURCE_FILE = "verification.txt";

  private static String DEFAULT_SUCCESS_HTML;
  private static String DEFAULT_BODY_HTML;
  private static String DEFAULT_BODY_TEXT;

  private static final String CUSTOM_BODY_HTML_RESOURCE_FILE = "fixtures/verification-email.html";
  private static final String CUSTOM_BODY_TEXT_RESOURCE_FILE = "fixtures/verification-email.txt";

  private static String CUSTOM_BODY_HTML_FILE_PATH;
  private static String CUSTOM_BODY_TEXT_FILE_PATH;

  @BeforeAll
  static void setup() {
    CUSTOM_BODY_HTML_FILE_PATH = TestResources.getPathOfResource(CUSTOM_BODY_HTML_RESOURCE_FILE);
    CUSTOM_BODY_TEXT_FILE_PATH = TestResources.getPathOfResource(CUSTOM_BODY_TEXT_RESOURCE_FILE);

    DEFAULT_SUCCESS_HTML = TestResources.readResourceFile(DEFAULT_SUCCESS_HTML_RESOURCE_FILE);
    DEFAULT_BODY_HTML = TestResources.readResourceFile(DEFAULT_BODY_HTML_RESOURCE_FILE);
    DEFAULT_BODY_TEXT = TestResources.readResourceFile(DEFAULT_BODY_TEXT_RESOURCE_FILE);

    DEFAULT_FACTORY = TestResources.readResourceYaml(
        EmailServiceFactory.class,
        "fixtures/configuration/email/message-options/default.yaml");
  }

  @Test
  void isDiscoverable() {
    // Make sure the types we specified in META-INF gets picked up
    assertTrue(new DiscoverableSubtypeResolver().getDiscoveredSubtypes()
        .contains(SesEmailServiceFactory.class));
    assertTrue(new DiscoverableSubtypeResolver().getDiscoveredSubtypes()
        .contains(DisabledEmailServiceFactory.class));
  }

  @Test
  void testDefaultMessageOptions() {
    EmailServiceFactory serviceFactory = TestResources.readResourceYaml(
        EmailServiceFactory.class,
        "fixtures/configuration/email/message-options/default.yaml");

    MessageOptions expected = new MessageOptions(
        "Account Verification", DEFAULT_BODY_HTML, DEFAULT_BODY_TEXT,
        "CODEGEN-URL", "CODEGEN-URL", DEFAULT_SUCCESS_HTML);

    assertEquals(expected, serviceFactory.getMessageOptions());
  }

  @Test
  void testMessageOptionsCustomPlaceholderWithNoCustomBody() {
    EmailServiceFactory serviceFactory = TestResources.readResourceYaml(
        EmailServiceFactory.class,
        "fixtures/configuration/email/message-options/placeholder-no-body.yaml");

    MessageOptions expected = new MessageOptions(
        "Test Subject", DEFAULT_BODY_HTML, DEFAULT_BODY_TEXT,
        "CODEGEN-URL", "CODEGEN-URL", DEFAULT_SUCCESS_HTML);

    assertEquals(expected, serviceFactory.getMessageOptions());
  }

  @Test
  void testMessageOptionsCustomPlaceholderWithCustomBodyText() {
    EmailServiceFactory serviceFactory = TestResources.readResourceYaml(
        EmailServiceFactory.class,
        "fixtures/configuration/email/message-options/placeholder-body-text.yaml");

    MessageOptions expected = new MessageOptions(
        "Test Subject", "bodyHtml", "bodyText",
        "CODEGEN-URL", "TEST-PLACEHOLDER", "successHtml");

    assertEquals(expected, serviceFactory.getMessageOptions("bodyHtml", "bodyText", "successHtml"));
  }

  @Test
  void testMessageOptionsCustomPlaceholderWithCustomBodyHtml() {
    EmailServiceFactory serviceFactory = TestResources.readResourceYaml(
        EmailServiceFactory.class,
        "fixtures/configuration/email/message-options/placeholder-body-html.yaml");

    MessageOptions expected = new MessageOptions(
        "Test Subject", "bodyHtml", "bodyText",
        "TEST-PLACEHOLDER", "CODEGEN-URL", "successHtml");

    assertEquals(expected, serviceFactory.getMessageOptions("bodyHtml", "bodyText", "successHtml"));
  }

  @Test
  void testMessageOptionsCustom() {
    EmailServiceFactory serviceFactory = TestResources.readResourceYaml(
        EmailServiceFactory.class,
        "fixtures/configuration/email/message-options/custom.yaml");

    MessageOptions expected = new MessageOptions(
        "Test Subject", "bodyHtml", "bodyText",
        "TEST-PLACEHOLDER", "TEST-PLACEHOLDER", "successHtml");

    assertEquals(expected, serviceFactory.getMessageOptions("bodyHtml", "bodyText", "successHtml"));
  }

  @Test
  void testGetFileContentsFromPath() {
    String expected = TestResources.readResourceFile(CUSTOM_BODY_TEXT_RESOURCE_FILE);

    assertEquals(expected,
        DEFAULT_FACTORY.getFileContents(CUSTOM_BODY_TEXT_FILE_PATH, CUSTOM_BODY_HTML_FILE_PATH));
  }

  @Test
  void testGetFileContentsFromInvalidPath() {
    EmailException invalidPath = assertThrows(EmailException.class,
        () -> DEFAULT_FACTORY.getFileContents("bad-path%name\0", CUSTOM_BODY_TEXT_FILE_PATH));
    EmailException readError = assertThrows(EmailException.class,
        () -> DEFAULT_FACTORY.getFileContents("bad-path%name", CUSTOM_BODY_TEXT_FILE_PATH));

    assertTrue(invalidPath.getCause() instanceof InvalidPathException);
    assertTrue(readError.getCause() instanceof IOException);

    try (MockedStatic<Files> filesMock = mockStatic(Files.class)) {
      filesMock.when(() -> Files.readString(any(Path.class), any(Charset.class)))
          .thenThrow(SecurityException.class);

      EmailException exception = assertThrows(EmailException.class,
          () -> DEFAULT_FACTORY.getFileContents("mytestpath", CUSTOM_BODY_TEXT_FILE_PATH));

      assertTrue(exception.getCause() instanceof SecurityException);
    }
  }

  @Test
  void testGetFileContentsFromInvalidResource() {
    IllegalStateException nonexistent = assertThrows(IllegalStateException.class,
        () -> DEFAULT_FACTORY.getFileContents(null, "not-exist"));

    assertTrue(nonexistent.getCause() instanceof IllegalArgumentException);

    try (MockedStatic<Resources> resourcesMock = mockStatic(Resources.class)) {
      resourcesMock.when(() -> Resources.toString(any(), any())).thenThrow(IOException.class);

      IllegalStateException exception = assertThrows(IllegalStateException.class,
          () -> DEFAULT_FACTORY.getFileContents(null, "not-exist\0"));

      assertTrue(exception.getCause() instanceof IOException);
    }
  }
}
