/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package org.jetbrains.idea.svn.info;

import com.intellij.execution.process.ProcessOutput;
import com.intellij.execution.process.ProcessOutputTypes;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.text.StringUtil;
import com.intellij.openapi.vcs.VcsException;
import com.intellij.openapi.vfs.CharsetToolkit;
import com.intellij.util.Consumer;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.idea.svn.api.BaseSvnClient;
import org.jetbrains.idea.svn.commandLine.*;
import org.tmatesoft.svn.core.*;
import org.tmatesoft.svn.core.wc.ISVNInfoHandler;
import org.tmatesoft.svn.core.wc.SVNInfo;
import org.tmatesoft.svn.core.wc.SVNRevision;
import org.tmatesoft.svn.core.wc2.SvnTarget;
import org.xml.sax.SAXException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * User: Irina.Chernushina
 * Date: 1/27/12
 * Time: 12:59 PM
 */
public class CmdInfoClient extends BaseSvnClient implements InfoClient {

  private static final Logger LOG = Logger.getInstance(CmdInfoClient.class);

  private String execute(@NotNull List<String> parameters, @NotNull File path) throws SVNException {
    // workaround: separately capture command output - used in exception handling logic to overcome svn 1.8 issue (see below)
    final ProcessOutput output = new ProcessOutput();
    LineCommandListener listener = new LineCommandAdapter() {
      @Override
      public void onLineAvailable(String line, Key outputType) {
        if (outputType == ProcessOutputTypes.STDOUT) {
          output.appendStdout(line);
        }
      }
    };

    try {
      CommandExecutor command = execute(myVcs, SvnTarget.fromFile(path), SvnCommandName.info, parameters, listener);

      return command.getOutput();
    }
    catch (VcsException e) {
      final String text = e.getMessage();
      final boolean notEmpty = !StringUtil.isEmptyOrSpaces(text);
      if (notEmpty && text.contains("W155010")) {
        // if "svn info" is executed for several files at once, then this warning could be printed only for some files, but info for other
        // files should be parsed from output
        return output.getStdout();
      }
      // not a working copy exception
      // "E155007: '' is not a working copy"
      if (notEmpty && text.contains("is not a working copy")) {
        if (StringUtil.isNotEmpty(output.getStdout())) {
          // TODO: Seems not reproducible in 1.8.4
          // workaround: as in subversion 1.8 "svn info" on a working copy root outputs such error for parent folder,
          // if there are files with conflicts.
          // but the requested info is still in the output except root closing tag
          return output.getStdout() + "</info>";
        } else {
          throw createError(SVNErrorCode.WC_NOT_WORKING_COPY, e);
        }
      // svn: E200009: Could not display info for all targets because some targets don't exist
      } else if (notEmpty && text.contains("some targets don't exist")) {
        throw createError(SVNErrorCode.ILLEGAL_TARGET, e);
      } else if (notEmpty && text.contains(String.valueOf(SVNErrorCode.WC_UPGRADE_REQUIRED.getCode()))) {
        throw createError(SVNErrorCode.WC_UPGRADE_REQUIRED, e);
      } else if (notEmpty &&
                 (text.contains("upgrade your Subversion client") ||
                  text.contains(String.valueOf(SVNErrorCode.WC_UNSUPPORTED_FORMAT.getCode())))) {
        throw createError(SVNErrorCode.WC_UNSUPPORTED_FORMAT, e);
      }
      throw createError(SVNErrorCode.IO_ERROR, e);
    }
  }

  @Nullable
  private static SVNInfo parseResult(@Nullable File base, @Nullable String result) throws SVNException {
    CollectInfoHandler handler = new CollectInfoHandler();

    parseResult(handler, base, result);

    return handler.getInfo();
  }

  private static void parseResult(@NotNull final ISVNInfoHandler handler, @Nullable File base, @Nullable String result) throws SVNException {
    if (StringUtil.isEmptyOrSpaces(result)) {
      return;
    }

    final SvnInfoHandler infoHandler = new SvnInfoHandler(base, new Consumer<SVNInfo>() {
      @Override
      public void consume(SVNInfo info) {
        try {
          handler.handleInfo(info);
        }
        catch (SVNException e) {
          throw new SvnExceptionWrapper(e);
        }
      }
    });

    parseResult(result, infoHandler);
  }

  private static void parseResult(@NotNull String result, @NotNull SvnInfoHandler handler) throws SVNException {
    try {
      SAXParser parser = SAXParserFactory.newInstance().newSAXParser();

      parser.parse(new ByteArrayInputStream(result.trim().getBytes(CharsetToolkit.UTF8_CHARSET)), handler);
    }
    catch (SvnExceptionWrapper e) {
      LOG.info("info output " + result);
      throw (SVNException) e.getCause();
    } catch (IOException e) {
      LOG.info("info output " + result);
      throw createError(SVNErrorCode.IO_ERROR, e);
    }
    catch (ParserConfigurationException e) {
      LOG.info("info output " + result);
      throw createError(SVNErrorCode.IO_ERROR, e);
    }
    catch (SAXException e) {
      LOG.info("info output " + result);
      throw createError(SVNErrorCode.IO_ERROR, e);
    }
  }

  @NotNull
  private static SVNException createError(@NotNull SVNErrorCode code, @Nullable Exception cause) {
    return new SVNException(SVNErrorMessage.create(code, cause), cause);
  }

  @NotNull
  private static List<String> buildParameters(@NotNull String path,
                                              @Nullable SVNRevision pegRevision,
                                              @Nullable SVNRevision revision,
                                              @Nullable SVNDepth depth) {
    List<String> parameters = ContainerUtil.newArrayList();

    CommandUtil.put(parameters, depth);
    CommandUtil.put(parameters, revision);
    CommandUtil.put(parameters, path, pegRevision);
    parameters.add("--xml");

    return parameters;
  }

  @Override
  public SVNInfo doInfo(File path, SVNRevision revision) throws SVNException {
    File base = path.isDirectory() ? path : path.getParentFile();
    base = CommandUtil.correctUpToExistingParent(base);
    if (base == null) {
      // very unrealistic
      throw new SVNException(SVNErrorMessage.create(SVNErrorCode.IO_ERROR, "Can not find existing parent file"));
    }

    return parseResult(base, execute(buildParameters(path.getAbsolutePath(), SVNRevision.UNDEFINED, revision, SVNDepth.EMPTY), path));
  }

  @Override
  public SVNInfo doInfo(SVNURL url, SVNRevision pegRevision, SVNRevision revision) throws SVNException {
    CommandExecutor command;
    try {
      command = execute(myVcs, SvnTarget.fromURL(url), SvnCommandName.info,
                        buildParameters(url.toDecodedString(), pegRevision, revision, SVNDepth.EMPTY), null);
    }
    catch (SvnBindException e) {
      throw createError(e.contains(SVNErrorCode.RA_ILLEGAL_URL) ? SVNErrorCode.RA_ILLEGAL_URL : SVNErrorCode.IO_ERROR, e);
    }

    return parseResult(null, command.getOutput());
  }

  @Override
  public void doInfo(@NotNull Collection<File> paths, @Nullable ISVNInfoHandler handler) throws SVNException {
    File base = ContainerUtil.getFirstItem(paths);

    if (base != null) {
      base = CommandUtil.correctUpToExistingParent(base);

      List<String> parameters = ContainerUtil.newArrayList();
      for (File file : paths) {
        CommandUtil.put(parameters, file);
      }
      parameters.add("--xml");

      // Currently do not handle exceptions here like in SvnVcs.handleInfoException - just continue with parsing in case of warnings for
      // some of the requested items
      String result = execute(parameters, base);
      if (handler != null) {
        parseResult(handler, base, result);
      }
    }
  }

  private static class CollectInfoHandler implements ISVNInfoHandler {

    @Nullable private SVNInfo myInfo;

    @Override
    public void handleInfo(SVNInfo info) throws SVNException {
      myInfo = info;
    }

    @Nullable
    public SVNInfo getInfo() {
      return myInfo;
    }
  }
}
