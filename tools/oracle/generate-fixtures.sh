#!/usr/bin/env bash
# Regenera os valores esperados das fixtures a partir do oráculo Python (independente da implementação Java)
# e confere que o oráculo rejeita todos os CSVs inválidos. Rodar na raiz do projeto.
set -euo pipefail
S=src/test/resources/scenarios
for f in $S/valid/*.csv; do python3 -I tools/oracle/oracle.py "$f" --wins > "${f%.csv}.wins.tsv"; done
python3 -I tools/oracle/oracle.py $S/official.csv --wins > $S/official.wins.tsv
python3 -I tools/oracle/oracle.py $S/official.csv > $S/official.intervals.json
for f in $S/awards/*.csv; do python3 -I tools/oracle/oracle.py "$f" > "${f%.csv}.intervals.json"; done
for f in $S/invalid/*.csv; do
  if python3 -I tools/oracle/oracle.py "$f" >/dev/null 2>&1; then echo "oráculo ACEITOU inválido: $f" >&2; exit 1; fi
done
echo "fixtures regeneradas"
