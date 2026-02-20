FROM registry.fedoraproject.org/fedora:latest

RUN dnf -y update; yum -y reinstall shadow-utils; \
yum -y install podman fuse-overlayfs  openssh-server openssh-clients rsync sudo curl --exclude container-selinux; \
rm -rf /var/cache /var/log/dnf* /var/log/yum.*

RUN useradd podman; \
echo podman:10000:5000 > /etc/subuid; \
echo podman:10000:5000 > /etc/subgid;

VOLUME /var/lib/containers
VOLUME /home/podman/.local/share/containers

RUN printf '[containers]\n\
netns="host"\n\
userns="host"\n\
ipcns="host"\n\
utsns="host"\n\
cgroupns="host"\n\
cgroups="disabled"\n\
log_driver = "k8s-file"\n\
[engine]\n\
cgroup_manager = "cgroupfs"\n\
events_logger="file"\n\
runtime="crun"\n' > /etc/containers/containers.conf
RUN mkdir -p /home/podman/.config/containers/
RUN printf '[containers]\n\
volumes = [\n\
        "/proc:/proc",\n\
]\n\
default_sysctls = []\n' > /home/podman/.config/containers/containers.conf

RUN chown podman:podman -R /home/podman

RUN chmod 644 /etc/containers/containers.conf
#RUN sed -i -e 's|^#mount_program|mount_program|g' -e '/additionalimage.*/a "/var/lib/shared",' -e 's|^mountopt[[:space:]]*=.*$|mountopt = "nodev,fsync=0"|g' /etc/containers/storage.conf

RUN mkdir -p /var/lib/shared/overlay-images /var/lib/shared/overlay-layers /var/lib/shared/vfs-images /var/lib/shared/vfs-layers
RUN touch /var/lib/shared/overlay-images/images.lock
RUN touch /var/lib/shared/overlay-layers/layers.lock
RUN touch /var/lib/shared/vfs-images/images.lock
RUN touch /var/lib/shared/vfs-layers/layers.lock

ENV _CONTAINERS_USERNS_CONFIGURED=""

RUN mkdir -p /var/run/sshd
#setup ssh for root user
RUN (umask 077 && test -d /root/.ssh || mkdir /root/.ssh)
RUN chmod 700 /root/.ssh
RUN printf 'root:password' | chpasswd
RUN ssh-keygen -A
RUN sed -i 's/^#\{0,1\}PermitRootLogin.*$/PermitRootLogin yes/' /etc/ssh/sshd_config
RUN sed -i 's/#AuthorizedKeysFile.*/AuthorizedKeysFile .ssh\/authorized_keys/g' /etc/ssh/sshd_config
RUN sed 's@session\s*required\s*pam_loginuid.so@session optional pam_loginuid.so@g' -i /etc/pam.d/sshd
EXPOSE 22

ENTRYPOINT /usr/sbin/sshd -D

