FROM debian:bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends squid ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /var/run/squid /var/spool/squid \
    && chown -R proxy:proxy /var/run/squid /var/spool/squid

COPY Backend/Ops/egress-proxy/squid.conf /etc/squid/squid.conf

USER proxy
EXPOSE 3128
ENTRYPOINT ["squid", "-N", "-f", "/etc/squid/squid.conf"]
