function S = StartMMStudio(varargin)
% STARTMMSTUDIO  Start MMStudio, setting up MATLAB's Java classpath as necessary
%
%   STUDIO = STARTMMSTUDIO()               Start MMStudio from the
%                                          Micro-Manager installation where
%                                          StartMMStudio.m is located
%   STUDIO = STARTMMSTUDIO(pathToMM)       Start MMStudio from the specified
%                                          Micro-Manager installation
%   S = STARTMMSTUDIO('-setup')            Set up the classpath for the
%                                          Micro-Manager installation where
%                                          StartMMStudio.m is located
%   S = STARTMMSTUDIO(pathToMM, '-setup')  Set up the classpath for the
%                                          specified Micro-Manager installation
%   S = STARTMMSTUDIO('-undosetup')        Remove classpath settings added by
%                                          this script
%   S = STARTMMSTUDIO(pathToMM, '-undosetup')  Remove classpath settings added
%                                              by this script
%
% pathToMM must be an absolute path if given.
%
% This function checks if MATLAB's Java classpath is set up correctly in order
% to run MMStudio, and if so, starts MMStudio.
%
% The return value is the org.micromanager.api.ScriptInterface object if
% MMStudio was started. If the classpath was not correct, [] is returned.
%
% If called with '-setup', adds the necessary classpath configuration. In this
% case, the return value is 1 (indicating that the classpath configuration was
% added, and MATLAB needs to be restarted) or 0 (indicating that the classpath
% configuration was already correct)
%
% If called with '-undosetup', reverses the effect of '-setup'. The return
% value is 1 (indicating that removal of the configuration was successful, and
% MATLAB needs to be restarted) or 0 (indicating that no changes were made)

% Author: Mark Tsuchida (inspired by an earlier script by Kyle Karhohs)
%
% Copyright (c) 2015, Regents of the University of California
% All rights reserved.
%
% Redistribution and use in source and binary forms, with or without
% modification, are permitted provided that the following conditions are met:
%
%   * Redistributions of source code must retain the above copyright notice,
%   this list of conditions and the following disclaimer.
%   * Redistributions in binary form must reproduce the above copyright notice,
%   this list of conditions and the following disclaimer in the documentation
%   and/or other materials provided with the distribution.
%   * Neither the name of the University of California nor the names of its
%   contributors may be used to endorse or promote products derived from this
%   software without specific prior written permission.
%
% THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
% AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
% IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
% ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE
% LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR
% CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF
% SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS
% INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN
% CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
% ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
% POSSIBILITY OF SUCH DAMAGE.

   %
   % Parse arguments
   %

   flag = '';  % '-setup', '-undosetup', or '' (indicating 'run' mode)
   pathToMM = '';
   argsOk = true;

   for k = 1:nargin
      arg = varargin{k};
      if strcmp(arg, '-setup') | strcmp(arg, '-undosetup')
         if isempty(flag)
            flag = arg;
         else
            argsOk = false;
         end
      elseif arg(1) == '-'  % bad flag
         argsOk = false;
      else
         if isempty(pathToMM)
            pathToMM = arg;
         else
            argsOk = false;
         end
      end
   end

   if ~argsOk
      error('Incorrect arguments; see help(''StartMMStudio'') for usage');
   end

   % The javaclasspath.txt file was added in R2012b.
   % The undocumented '<before>' feature in javaclasspath.txt requires R2013a.
   release = version('-release');
   if str2num(release) < 2013
      error('MATLAB R2013a or later required');
   end

   % Use the location of this script as the default Micro-Manager root
   if isempty(pathToMM)
      [pathToMM b e] = fileparts(mfilename('fullpath'));
   end

   disp(['Using Micro-Manager installation at: ' pathToMM]);
   CheckMMInstallation(pathToMM);

   if strcmp(flag, '-setup')
      S = SetUpMMClasspath(pathToMM);
   elseif strcmp(flag, '-undosetup')
      S = UndoMMClasspath(pathToMM);
   else
      S = DoStartMMStudio(pathToMM);
   end
end


%
%
%


function status = SetUpMMClasspath(pathToMM)
   status = 0;
   pathsOk = CheckMMClasspath(pathToMM);
   if pathsOk
      disp('Classpath is already set up for Micro-Manager');
      return
   end
   disp('Setting up classpath for Micro-Manager');
   RemoveMMClasspath(pathToMM);
   AppendMMClasspath(pathToMM);
   disp(['Updated ' GetJavaClasspathFile()]);
   disp('Restart MATLAB before continuing.');
   status = 1;
   return
end


function status = UndoMMClasspath(pathToMM)
   status = RemoveMMClasspath(pathToMM);
   if status
      disp(['Updated ' GetJavaClasspathFile()]);
      disp('Restart MATLAB before continuing.');
   else
      disp('No change to classpath was necessary');
   end
end


function studio = DoStartMMStudio(pathToMM)
   pathsOk = CheckMMClasspath(pathToMM);
   if ~pathsOk
      disp('Classpath must be set up; see help(''StartMMStudio'')');
      studio = [];
      return
   end
   studio = CreateMMStudio(pathToMM);
end


%
%
%


function [] = CheckMMInstallation(pathToMM)
   expectedFiles = {'ij.jar', ...
         fullfile('plugins', 'Micro-Manager', 'MMJ_.jar')};
   for k = 1:length(expectedFiles)
      expectedFile = fullfile(pathToMM, expectedFiles{k});
      if ~exist(expectedFile, 'file')
         error(['%s does not look like a Micro-Manager installation\n' ...
               'File missing: %s'], pathToMM, expectedFile);
      end
   end
end


function ok = CheckMMClasspath(pathToMM)
   % Scan pathToMM and find exact set of needed JARs
   % Scan javaclasspath('-all'):
   %   - Mark presence of needed JARs
   %   - If JAR with same leaf name seen before needed one, error
   %   - If google-collect seen before guava, error
   %   - When done, if any needed ones weren't seen, error
   % Error returns 0, no error()

   ok = false;

   mmJars = GetMMJars(pathToMM);
   mmJarLeafNames = cellfun(@GetLeafName, mmJars, 'UniformOutput', false);
   guavaJar = GetMMGuavaJar(pathToMM);

   cp = javaclasspath('-all');

   mmJarSeen = zeros(length(mmJars), 1);
   guavaSeen = false;

   % Do a single pass through all classpath entries
   for k = 1:length(cp)
      cpEntry = cp{k};

      % Is this any of the needed JARs?
      i = find(strcmpi(mmJars, cpEntry));
      if ~isempty(i)
         mmJarSeen(i(1)) = 1;  % Mark as seen

         % Is this the needed Guava JAR?
         if strcmpi(guavaJar, cpEntry)
            guavaSeen = true;
         end
         continue
      end

      % Or is this a JAR at the wrong path?
      cpLeafName = GetLeafName(cpEntry);
      i = find(strcmpi(mmJarLeafNames, cpLeafName));
      if ~isempty(i) & mmJarSeen(i(1))
         disp(['JAR from wrong path found in classpath: ' cpEntry]);
         return
      end

      % Or is this Google Collections?
      if strcmp(cpLeafName, 'google-collect.jar')
         if ~guavaSeen
            disp('google-collect.jar found before guava in classpath');
            return
         end
      end
   end

   % Did we see all needed JARs?
   if ~all(mmJarSeen)
      disp('JAR(s) missing from classpath:');
      for n = 1:length(mmJars)
         if ~mmJarSeen(n)
            disp(mmJars{n});
         end
      end
      return
   end

   ok = true;
end


function [] = AppendMMClasspath(pathToMM)
   % Unconditionally append all needed JARs to javaclasspath.txt

   mmJars = GetMMJars(pathToMM);
   guavaJar = GetMMGuavaJar(pathToMM);

   fid = fopen(GetJavaClasspathFile(), 'at');

   % List all but guava
   fprintf(fid, '\n');
   for k = 1:length(mmJars)
      jarName = mmJars{k};
      if ~strcmpi(jarName, guavaJar)
         fprintf(fid, '%s\n', jarName);
      end
   end

   % Finally, list guava after <before>.
   % This is an undocumented trick (R2013a+), which causes all JARs listed
   % after the '<before>' line to be placed before MATLAB's system classpath.
   % In our case, we need guava to come before google-collect.
   fprintf(fid, '<before>\n');
   fprintf(fid, '%s\n', guavaJar);

   fclose(fid);
end


function changed = RemoveMMClasspath(pathToMM)
   % Scan javaclasspath.txt, and keep JARs that are unrelated to MM.
   % If any were removed, rewrite javaclasspath.txt and return 1
   % Else return 0
   % (Remove '<before>' if it is leftover at end of file)

   mmJars = GetMMJars(pathToMM);
   mmJarLeafNames = cellfun(@GetLeafName, mmJars, 'UniformOutput', false);

   cpFileEntries = {};
   k = 0;

   beforeIndex = 0;
   removalNeeded = false;

   fid = fopen(GetJavaClasspathFile(), 'rt');
   if fid == -1
      if exist(GetJavaClasspathFile(), 'file')
         error('%s exists but cannot be read', GetJavaClasspathFile());
      else
         % No need to do anything if file doesn't exist.
         changed = false;
         return
      end
   end

   line = fgetl(fid);
   while ischar(line)
      i = regexp(line, '^[ \t]*$');
      if ~isempty(i)
         k = k + 1; cpFileEntries{k} = '';
         continue
      end

      i = regexp(line, '^ *<before> *$');
      if ~isempty(i) & beforeIndex == 0  % Keep only the first '<before>'
         k = k + 1; cpFileEntries{k} = '<before>';
         beforeIndex = k;
         continue
      end

      % Otherwise we have a JAR entry
      leafName = GetLeafName(line);
      if any(strcmpi(mmJars, line))
         removalNeeded = true;
      elseif any(strcmpi(mmJarLeafNames, leafName))
         jcpFile = GetJavaClasspathFile();
         edit(jcpFile);
         error('Entry %s in %s conflicts with Micro-Manager JAR; cannot automatically configure. Please manually edit %s to remove conflicting entries', ...
               line, jcpFile, jcpFile);
      else
         k = k + 1; cpFileEntries{k} = line;
      end

      line = fgetl(fid);
   end

   fclose(fid);

   if ~removalNeeded
      changed = false;
      return
   end

   % Trim empty lines and '<before>' at end
   while k > 0 & (isempty(cpFileEntries{k}) | ...
         strcmp(cpFileEntries{k}, '<before>'))
      cpFileEntries(k) = [];
      k = k - 1;
   end
   if beforeIndex > k
      beforeIndex = 0;
   end

   % Now we have what we want to keep; rewrite the file.
   fid = fopen(GetJavaClasspathFile(), 'wt');

   for k = 1:length(cpFileEntries)
      entry = cpFileEntries{k};
      fprintf(fid, '%s\n', entry);
   end

   fclose(fid);

   changed = true;
end


function studio = CreateMMStudio(pathToMM)
   % Instantiates MMStudio, assuming the classpath is correct

   java.lang.System.setProperty('org.micromanager.plugin.path', ...
         fullfile(pathToMM, 'mmplugins'));
   java.lang.System.setProperty('org.micromanager.autofocus.path', ...
         fullfile(pathToMM, 'mmautofocus'));

   % TODO We could do some health checks on MMCoreJ here

   studio = org.micromanager.MMStudio(false);
end


%
%
%

function mmJars = GetMMJars(pathToMM)
   % Return cell array of jar paths belonging to MM

   mmJars = {};
   k = 0;

   jarName = fullfile(pathToMM, 'ij.jar');
   k = k + 1; mmJars{k} = jarName;

   jarPath = fullfile(pathToMM, 'plugins', 'Micro-Manager');
   for jarFile = dir(fullfile(jarPath, '*.jar'))'
      jarName = fullfile(jarPath, jarFile.name);
      k = k + 1; mmJars{k} = jarName;
   end

   mmJars = mmJars';
end


function guavaPath = GetMMGuavaJar(pathToMM)
   jarDir = fullfile(pathToMM, 'plugins', 'Micro-Manager');
   P = dir(fullfile(jarDir, 'guava-*.jar'));
   if ~isempty(P)
      guavaPath = fullfile(jarDir, P(1).name);
   end
end


function leaf = GetLeafName(path)
   [dirname basename extension] = fileparts(path);
   leaf = [basename extension];
end


function jcpFile = GetJavaClasspathFile()
   jcpFile = fullfile(prefdir, 'javaclasspath.txt');
end
