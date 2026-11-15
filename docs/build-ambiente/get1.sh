#!/bin/bash
# Baixa um caminho do Maven Central para /opt/m2local, com poucas tentativas espaçadas.
BASE=https://repo.maven.apache.org/maven2
rel="$1"; out="/opt/m2local/$rel"
[ -s "$out" ] && exit 0
[ -e "$out.absent" ] && exit 1
mkdir -p "$(dirname "$out")"
nf=0
for t in 1 2 3 4 5 6 7 8; do
  code=$(curl -s -o "$out.part" -w '%{http_code}' -m 180 "$BASE/$rel")
  if [ "$code" = "200" ]; then mv "$out.part" "$out"; exit 0; fi
  rm -f "$out.part"
  if [ "$code" = "404" ]; then nf=$((nf+1)); [ $nf -ge 4 ] && { touch "$out.absent"; exit 1; }; sleep 1; else sleep $((2 + t)); fi
done
exit 1
