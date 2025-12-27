FROM ubuntu:jammy AS build
# jammy is LTS; see https://www.releases.ubuntu.com/ https://hub.docker.com/_/ubuntu
LABEL org.opencontainers.image.source https://github.com/temperlang/temper/docker
ARG TARGETARCH  # set by docker buildx build based on platform

SHELL ["/bin/bash", "-c"]
RUN TERM=DUMB apt-get update && apt-get upgrade -y && \
    apt-get install -y --no-install-recommends ca-certificates \
    libncurses-dev libbz2-dev libzip-dev libffi-dev libreadline-dev \
    libssl-dev libsqlite3-dev liblzma-dev libgmp-dev libdb-dev \
    bsdmainutils clang curl gcc git make pkg-config wget zip unzip

# Don't use --create-home; the skeleton files aren't really helpful
# The runner utility expects username 'temper' and uid 1000
RUN useradd --shell /bin/bash --uid 1000 --user-group --comment 'Temper user' temper && \
    mkdir -p /home/temper && chown -R temper:temper /home/temper

USER temper
WORKDIR /home/temper
ENV PATH="/home/temper/.asdf/shims:/home/temper/.local/bin:${PATH}"

# Allow different tools per target architecture
COPY --chown=temper home/* /home/temper/

# Install the asdf version manager and plugin manager, and then install tools.
RUN source "$HOME/utils.sh" && asdf-bootstrap && asdf-setup && post-asdf-helpers
RUN rm "$HOME/utils.sh"

FROM ubuntu:jammy
LABEL org.opencontainers.image.source https://github.com/temperlang/temper/docker
ARG TARGETARCH  # set by docker buildx build based on platform

SHELL ["/bin/bash", "-c"]

# TODO: more systematic dependencies
# .NET deps: https://learn.microsoft.com/en-us/dotnet/core/install/linux-ubuntu#dependencies
RUN TERM=DUMB apt-get update && apt-get upgrade -y && \
    apt-get install -y --no-install-recommends ca-certificates \
    libncursesw6 libbz2-1.0 libzip4 libffi8 libreadline8 \
    libssl3 libsqlite3-0 liblzma5 \
    libc6-dev libgcc-s1 libicu70 liblttng-ust1 libstdc++6 libunwind8 zlib1g \
    bsdmainutils curl gcc git wget zip unzip && \
    apt-get clean

# Don't use --create-home; the skeleton files aren't really helpful
# The runner utility expects username 'temper' and uid 1000
RUN useradd --shell /bin/bash --uid 1000 --user-group --comment 'Temper user' temper

USER temper
WORKDIR /home/temper
# For some reason, I get owned by root without this `ls`???
# Maybe some cache issue, but I'll run with it for now, anyway.
RUN ls -ld /home/temper
ENV DOTNET_CLI_TELEMETRY_OPTOUT="1"
ENV LD_LIBRARY_PATH=/home/temper/.local/lib
ENV PATH="/home/temper/.asdf/shims:/home/temper/.local/bin:${PATH}"

COPY --from=build /home/temper/ /home/temper/
