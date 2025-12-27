ARG BASE_IMAGE_REF

FROM --platform=$TARGETPLATFORM $BASE_IMAGE_REF
ARG TARGETARCH

LABEL org.opencontainers.image.source https://github.com/temperlang/temper/docker

COPY --chown=temper $TARGETARCH/launcher cli/bin/temper .local/bin/
COPY --chown=temper cli/lib/*.jar .local/lib/
RUN mkdir -p /home/temper/work

WORKDIR /home/temper/work
ENTRYPOINT ["/home/temper/.local/bin/launcher"]
