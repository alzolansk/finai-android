#!/bin/bash
cd /home/user/finai-android
BASE=https://repo.maven.apache.org/maven2
for i in $(seq 1 80); do
  ./gradlew "$@" --console=plain > /tmp/claude-0/base-build.log 2>&1 && { echo "OK iteration $i" >> /tmp/claude-0/retry.log; exit 0; }
  urls=$(grep -o "https://repo.maven.apache.org/maven2/[^ '\"]*" /tmp/claude-0/base-build.log | sort -u)
  n=$(echo "$urls" | grep -c . )
  echo "iteration $i: $n urls missing" >> /tmp/claude-0/retry.log
  [ "$n" = "0" ] && { echo "FAILED for another reason" >> /tmp/claude-0/retry.log; exit 2; }
  list=/tmp/claude-0/fetch-list.txt; : > $list
  for u in $urls; do
    rel=${u#$BASE/}; echo "$rel" >> $list
    dir=$(dirname "$rel"); art=$(basename "$(dirname "$dir")"); ver=$(basename "$dir")
    case "$rel" in
      *.jar|*.aar|*.klib) echo "$dir/$art-$ver.pom" >> $list; echo "$dir/$art-$ver.module" >> $list ;;
      *.pom) echo "$dir/$art-$ver.module" >> $list; echo "$dir/$art-$ver.jar" >> $list ;;
      *.module) echo "$dir/$art-$ver.pom" >> $list ;;
    esac
  done
  sort -u $list | xargs -P 3 -n 1 /tmp/claude-0/get1.sh
done
echo "GAVE UP" >> /tmp/claude-0/retry.log; exit 1
