package io.hyperfoil.tools.qdup.stream;

import org.junit.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ScheduledThreadPoolExecutor;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.*;

public class SessionStreamsTest {


   private void writeSlowly(SessionStreams streams,String input){
      byte buffer[] = input.getBytes();
      for(int i=0; i<buffer.length; i++){
         try {
            streams.write(buffer,i,1);
         } catch (IOException e) {
            fail(e.getMessage());
         }
      }
   }

   private SessionStreams getStreams(){
      SessionStreams sessionStreams = new SessionStreams("test",new ScheduledThreadPoolExecutor(Runtime.getRuntime().availableProcessors()/2,new ThreadFactory() {
         AtomicInteger count = new AtomicInteger(0);
         @Override
         public Thread newThread(Runnable runnable) {
            return new Thread(runnable,"schedule-"+count.getAndAdd(1));
         }
      }));
      return sessionStreams;
   }

   private void write(SessionStreams sessionStreams, String toWrite) throws IOException {
      sessionStreams.getEscapeFilteredStream().write(toWrite.getBytes(),0,toWrite.getBytes().length);
   }

   @Test
   public void env_slow(){
      String input = "LS_COLORS=rs=0:di=38;5;33:ln=38;5;51:mh=00:pi=40;38;5;11:so=38;5;13:do=38;5;5:bd=48;5;232;38;5;11:cd=48;5;232;38;5;3:or=48;5;232;38;5;9:mi=01;05;37;41:su=48;5;196;38;5;15:sg=48;5;11;38;5;16:ca=48;5;196;38;5;226:tw=48;5;10;38;5;16:ow=48;5;10;38;5;21:st=48;5;21;38;5;15:ex=38;5;40:*.tar=38;5;9:*.tgz=38;5;9:*.arc=38;5;9:*.arj=38;5;9:*.taz=38;5;9:*.lha=38;5;9:*.lz4=38;5;9:*.lzh=38;5;9:*.lzma=38;5;9:*.tlz=38;5;9:*.txz=38;5;9:*.tzo=38;5;9:*.t7z=38;5;9:*.zip=38;5;9:*.z=38;5;9:*.dz=38;5;9:*.gz=38;5;9:*.lrz=38;5;9:*.lz=38;5;9:*.lzo=38;5;9:*.xz=38;5;9:*.zst=38;5;9:*.tzst=38;5;9:*.bz2=38;5;9:*.bz=38;5;9:*.tbz=38;5;9:*.tbz2=38;5;9:*.tz=38;5;9:*.deb=38;5;9:*.rpm=38;5;9:*.jar=38;5;9:*.war=38;5;9:*.ear=38;5;9:*.sar=38;5;9:*.rar=38;5;9:*.alz=38;5;9:*.ace=38;5;9:*.zoo=38;5;9:*.cpio=38;5;9:*.7z=38;5;9:*.rz=38;5;9:*.cab=38;5;9:*.wim=38;5;9:*.swm=38;5;9:*.dwm=38;5;9:*.esd=38;5;9:*.jpg=38;5;13:*.jpeg=38;5;13:*.mjpg=38;5;13:*.mjpeg=38;5;13:*.gif=38;5;13:*.bmp=38;5;13:*.pbm=38;5;13:*.pgm=38;5;13:*.ppm=38;5;13:*.tga=38;5;13:*.xbm=38;5;13:*.xpm=38;5;13:*.tif=38;5;13:*.tiff=38;5;13:*.png=38;5;13:*.svg=38;5;13:*.svgz=38;5;13:*.mng=38;5;13:*.pcx=38;5;13:*.mov=38;5;13:*.mpg=38;5;13:*.mpeg=38;5;13:*.m2v=38;5;13:*.mkv=38;5;13:*.webm=38;5;13:*.ogm=38;5;13:*.mp4=38;5;13:*.m4v=38;5;13:*.mp4v=38;5;13:*.vob=38;5;13:*.qt=38;5;13:*.nuv=38;5;13:*.wmv=38;5;13:*.asf=38;5;13:*.rm=38;5;13:*.rmvb=38;5;13:*.flc=38;5;13:*.avi=38;5;13:*.fli=38;5;13:*.flv=38;5;13:*.gl=38;5;13:*.dl=38;5;13:*.xcf=38;5;13:*.xwd=38;5;13:*.yuv=38;5;13:*.cgm=38;5;13:*.emf=38;5;13:*.ogv=38;5;13:*.ogx=38;5;13:*.aac=38;5;45:*.au=38;5;45:*.flac=38;5;45:*.m4a=38;5;45:*.mid=38;5;45:*.midi=38;5;45:*.mka=38;5;45:*.mp3=38;5;45:*.mpc=38;5;45:*.ogg=38;5;45:*.ra=38;5;45:*.wav=38;5;45:*.oga=38;5;45:*.opus=38;5;45:*.spx=38;5;45:*.xspf=38;5;45:|\n" +
              "SSH_CONNECTION=10.22.8.147 57416 10.1.184.215 22\n" +
              "LANG=en_US.UTF-8\n" +
              "HISTCONTROL=ignoredups\n" +
              "HOSTNAME=mwperf-server01.perf.lab.eng.rdu2.redhat.com\n" +
              "OLDPWD=/home/jenkins\n" +
              "SDKMAN_CANDIDATES_API=https://api.sdkman.io/2\n" +
              "JAVA_HOME=/home/jenkins/.sdkman/candidates/java/current\n" +
              "XDG_SESSION_ID=1230\n" +
              "S_COLORS=auto\n" +
              "USER=jenkins\n" +
              "SELINUX_ROLE_REQUESTED=\n" +
              "PWD=/home/jenkins/toolchain-e2e\n" +
              "QUAY_NAMESPACE=wreicher\n" +
              "HOME=/home/jenkins\n" +
              "SSH_CLIENT=10.22.8.147 57416 22\n" +
              "SELINUX_LEVEL_REQUESTED=\n" +
              "SDKMAN_DIR=/home/jenkins/.sdkman\n" +
              "SSH_TTY=/dev/pts/2\n" +
              "MAIL=/var/spool/mail/jenkins\n" +
              "TERM=xterm-256color\n" +
              "SHELL=/bin/bash\n" +
              "XMODIFIERS=@im=ibus\n" +
              "SDKMAN_CANDIDATES_DIR=/home/jenkins/.sdkman/candidates\n" +
              "SELINUX_USE_CURRENT_RANGE=\n" +
              "SHLVL=1\n" +
              "MANPATH=:/opt/puppetlabs/puppet/share/man\n" +
              "LOGNAME=jenkins\n" +
              "DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1001/bus\n" +
              "XDG_RUNTIME_DIR=/run/user/1001\n" +
              "PATH=/home/jenkins/.sdkman/candidates/java/current/bin:/home/jenkins/.local/bin:/home/jenkins/bin:/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/opt/puppetlabs/bin\n" +
              "SDKMAN_VERSION=5.10.0+617\n" +
              "HISTSIZE=1000\n" +
              "SDKMAN_PLATFORM=LinuxX64\n" +
              "LESSOPEN=||/usr/bin/lesspipe.sh %s\n" +
              "_=/usr/bin/env\n";
      SessionStreams sessionStreams = getStreams();

      sessionStreams.setCommand("env");
      writeSlowly(
         sessionStreams,
        "env\n" +
         input
      );
      String output = sessionStreams.currentOutput();

      assertFalse("do not merge env lines because of previous filter",output.contains(":SSH_CONNECTION"));
      assertTrue("do not remove trailing command",output.endsWith("env\n"));
      assertEquals(input,output);
   }

   @Test
   public void env_separate_writes(){
      SessionStreams sessionStreams = getStreams();
      try {
         write(sessionStreams,"LOGNAME=jenkins\n" +
                 "DBUS_SESSION_BUS_ADDRESS=unix:path=/run/user/1000/bus\n" +
                 "XDG_RUNTIME_DIR=/run/user/1000");
         write(sessionStreams,"\n");
         write(sessionStreams,
                 "PATH=/home/jenkins/.local/bin:/home/jenkins/bin:/usr/local/bin:/usr/bin:/usr/local/sbin:/usr/sbin:/opt/puppetlabs/bin\n" +
                         "PS1=<_#%@_qdup_@%#_>\n");
         String output = sessionStreams.currentOutput();

      } catch (IOException e) {
         fail(e.getMessage());
      }
   }

   @Test
   public void line_emitting_post_filter(){
      SessionStreams sessionStreams = getStreams();

      List<String> emitted = new ArrayList<>();
      sessionStreams.getLineEmittingStream().addConsumer(emitted::add);

      sessionStreams.getFilteredStream().addFilter("command","command","");
      try{
         write(sessionStreams,"command");
         write(sessionStreams,"\r\n");
         write(sessionStreams,"foo\r\n");
         write(sessionStreams,"bar\r\n");
      }catch(IOException e){
         fail(e.getMessage());
      }
      assertEquals("expect 2 entries in array: "+emitted.toString(),2,emitted.size());
   }
   @Test
   public void line_emitting_with_filter(){
      SessionStreams sessionStreams = getStreams();

      List<String> emitted = new ArrayList<>();
      sessionStreams.getLineEmittingStream().addConsumer(emitted::add);

      sessionStreams.getFilteredStream().addFilter("command","command","");
      try{
         write(sessionStreams,"command\r\n");
         write(sessionStreams,"foo\r\n");
         write(sessionStreams,"bar\r\n");
      }catch(IOException e){
         fail(e.getMessage());
      }
      assertEquals("expect 2 entries in array: "+emitted.toString(),2,emitted.size());



   }

}
