/*
 * Copyright 2017 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.dlp;

import com.google.cloud.dlp.v2beta1.DlpServiceClient;
import com.google.privacy.dlp.v2beta1.ContentItem;
import com.google.privacy.dlp.v2beta1.InfoType;
import com.google.privacy.dlp.v2beta1.InspectConfig;
import com.google.privacy.dlp.v2beta1.Likelihood;
import com.google.privacy.dlp.v2beta1.RedactContentRequest;
import com.google.privacy.dlp.v2beta1.RedactContentRequest.ImageRedactionConfig;
import com.google.privacy.dlp.v2beta1.RedactContentRequest.ReplaceConfig;
import com.google.privacy.dlp.v2beta1.RedactContentResponse;
import com.google.protobuf.ByteString;
import java.io.FileOutputStream;
import java.net.URLConnection;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import javax.activation.MimetypesFileTypeMap;
import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.DefaultParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.OptionGroup;
import org.apache.commons.cli.Options;
import org.apache.commons.cli.ParseException;

public class Redact {

  private static void redactString(
      String string, String replacement, Likelihood minLikelihood, List<InfoType> infoTypes)
      throws Exception {
    // [START dlp_redact_string]
    // Instantiate the DLP client
    try (DlpServiceClient dlpClient = DlpServiceClient.create()) {
      // The minimum likelihood required before returning a match
      // eg.minLikelihood = LIKELIHOOD_VERY_LIKELY;
      InspectConfig inspectConfig =
          InspectConfig.newBuilder()
              .addAllInfoTypes(infoTypes)
              .setMinLikelihood(minLikelihood)
              .build();

      ContentItem contentItem =
          ContentItem.newBuilder()
              .setType("text/plain")
              .setData(ByteString.copyFrom(string.getBytes()))
              .build();

      List<ReplaceConfig> replaceConfigs = new ArrayList<>();

      if (infoTypes.isEmpty()) {
        // replace all detected sensitive elements with replacement string
        replaceConfigs.add(ReplaceConfig.newBuilder().setReplaceWith(replacement).build());
      } else {
        // Replace select info types with chosen replacement string
        for (InfoType infoType : infoTypes) {
          replaceConfigs.add(
              ReplaceConfig.newBuilder().setInfoType(infoType).setReplaceWith(replacement).build());
        }
      }

      RedactContentRequest request = RedactContentRequest.newBuilder()
          .setInspectConfig(inspectConfig)
          .addAllItems(Collections.singletonList(contentItem))
          .addAllReplaceConfigs(replaceConfigs)
          .build();

      RedactContentResponse contentResponse = dlpClient.redactContent(request);
      for (ContentItem responseItem : contentResponse.getItemsList()) {
        // print out string with redacted content
        System.out.println(responseItem.getData().toStringUtf8());
      }
    }
    // [END dlp_redact_string]
  }

  private static void redactImage(
      String filePath, Likelihood minLikelihood, List<InfoType> infoTypes, String outputPath)
      throws Exception {
    // [START dlp_redact_image]
    // Instantiate the DLP client
    try (DlpServiceClient dlpClient = DlpServiceClient.create()) {
      // The path to a local file to inspect. Can be a JPG or PNG image file.
      //  filePath = 'path/to/image.png'
      // detect file mime type, default to application/octet-stream
      String mimeType = URLConnection.guessContentTypeFromName(filePath);
      if (mimeType == null) {
        mimeType = MimetypesFileTypeMap.getDefaultFileTypeMap().getContentType(filePath);
      }
      if (mimeType == null) {
        mimeType = "application/octet-stream";
      }

      byte[] data = Files.readAllBytes(Paths.get(filePath));

      // The minimum likelihood required before redacting a match
      //  minLikelihood = 'LIKELIHOOD_UNSPECIFIED'

      // The infoTypes of information to redact
      // infoTypes = [{ name: 'EMAIL_ADDRESS' }, { name: 'PHONE_NUMBER' }]

      // The local path to save the resulting image to.
      // outputPath = 'result.png'

      InspectConfig inspectConfig =
          InspectConfig.newBuilder()
              .addAllInfoTypes(infoTypes)
              .setMinLikelihood(minLikelihood)
              .build();
      ContentItem contentItem =
          ContentItem.newBuilder().setType(mimeType).setData(ByteString.copyFrom(data)).build();

      List<ImageRedactionConfig> imageRedactionConfigs = new ArrayList<>();
      for (InfoType infoType : infoTypes) {
        // clear the specific info type if detected in the image
        // use .setRedactionColor to color detected info type without clearing
        ImageRedactionConfig imageRedactionConfig =
            ImageRedactionConfig.newBuilder().setInfoType(infoType).clearTarget().build();
        imageRedactionConfigs.add(imageRedactionConfig);
      }
      RedactContentRequest redactContentRequest =
          RedactContentRequest.newBuilder()
              .setInspectConfig(inspectConfig)
              .addAllImageRedactionConfigs(imageRedactionConfigs)
              .addItems(contentItem)
              .build();

      RedactContentResponse contentResponse = dlpClient.redactContent(redactContentRequest);
      for (ContentItem responseItem : contentResponse.getItemsList()) {
        // redacted image data
        ByteString redactedImageData = responseItem.getData();
        FileOutputStream outputStream = new FileOutputStream(outputPath);
        outputStream.write(redactedImageData.toByteArray());
        outputStream.close();
      }
      // [END dlp_redact_image]
    }
  }

  /** Command line application to redact strings, images using the Data Loss Prevention API. */
  public static void main(String[] args) throws Exception {
    OptionGroup optionsGroup = new OptionGroup();
    optionsGroup.setRequired(true);
    Option stringOption = new Option("s", "string", true, "redact string");
    optionsGroup.addOption(stringOption);

    Option fileOption = new Option("f", "file path", true, "redact input file path");
    optionsGroup.addOption(fileOption);

    Options commandLineOptions = new Options();
    commandLineOptions.addOptionGroup(optionsGroup);

    Option minLikelihoodOption =
        Option.builder("minLikelihood").hasArg(true).required(false).build();

    commandLineOptions.addOption(minLikelihoodOption);

    Option replaceOption =
        Option.builder("r").longOpt("replace string").hasArg(true).required(false).build();
    commandLineOptions.addOption(replaceOption);

    Option infoTypesOption = Option.builder("infoTypes").hasArg(true).required(false).build();
    infoTypesOption.setArgs(Option.UNLIMITED_VALUES);
    commandLineOptions.addOption(infoTypesOption);

    Option outputFilePathOption =
        Option.builder("o").hasArg(true).longOpt("outputFilePath").required(false).build();
    commandLineOptions.addOption(outputFilePathOption);

    CommandLineParser parser = new DefaultParser();
    HelpFormatter formatter = new HelpFormatter();
    CommandLine cmd;

    try {
      cmd = parser.parse(commandLineOptions, args);
    } catch (ParseException e) {
      System.out.println(e.getMessage());
      formatter.printHelp(Redact.class.getName(), commandLineOptions);
      System.exit(1);
      return;
    }

    String replacement = cmd.getOptionValue(replaceOption.getOpt(), "_REDACTED_");

    List<InfoType> infoTypesList = new ArrayList<>();
    String[] infoTypes = cmd.getOptionValues(infoTypesOption.getOpt());
    if (infoTypes != null) {
      for (String infoType : infoTypes) {
        infoTypesList.add(InfoType.newBuilder().setName(infoType).build());
      }
    }
    Likelihood minLikelihood =
        Likelihood.valueOf(
            cmd.getOptionValue(
                minLikelihoodOption.getOpt(), Likelihood.LIKELIHOOD_UNSPECIFIED.name()));

    // string inspection
    if (cmd.hasOption("s")) {
      String source = cmd.getOptionValue(stringOption.getOpt());
      redactString(source, replacement, minLikelihood, infoTypesList);
    } else if (cmd.hasOption("f")) {
      String filePath = cmd.getOptionValue(fileOption.getOpt());
      String outputFilePath = cmd.getOptionValue(outputFilePathOption.getOpt());
      redactImage(filePath, minLikelihood, infoTypesList, outputFilePath);
    }
  }
}
