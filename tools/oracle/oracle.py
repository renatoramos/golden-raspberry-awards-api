#!/usr/bin/env python3
"""Oráculo independente (v2): intervalos min/max entre vitórias ADJACENTES por produtor.
Uso: python3 -I oracle.py arquivo.csv [-v]
Suposições aprovadas: S1 empates todos | S2 pares adjacentes, empates do mesmo produtor entram | S3 1 vitória ignorada
S4 mesmo ano conta 1 | S5 sem par => listas vazias | S6 colunas por nome | S7 (revisada) obrigatórias: year, title, producers; opcionais: winner, studios
S8 winner=='yes' (case-insens.) | S9 (revisada) split ',' c/ ou s/ espaço, ', and', ' and ' | S10 sem normalização | S11 ';' padrão, ',' pelo
cabeçalho, RFC 4180, BOM, CRLF | S12 ordem previousWin, producer | S14 linha inválida => erro com nº da linha."""
import csv, io, json, re, sys
from collections import defaultdict

REQUIRED = ('year', 'title', 'producers')      # winner e studios são opcionais
SPLIT = re.compile(r'\s*,\s*(?:and\s+)?|\s+and\s+')   # ',' (com ou sem espaço), ', and', ' and '

class CsvError(Exception):
    pass

def producers(cell):
    return [p.strip() for p in SPLIT.split(cell) if p.strip()]

def detect_delimiter(text):
    header = text.split('\n', 1)[0].rstrip('\r')
    best = None
    for d in (';', ','):                      # ';' tem precedência em empate
        cols = {c.strip().lower() for c in next(csv.reader([header], delimiter=d))}
        score = sum(r in cols for r in REQUIRED + ('studios', 'winner'))
        if best is None or score > best[0]:
            best = (score, d)
    return best[1]

def compute(path):
    with open(path, encoding='utf-8-sig', newline='') as fh:
        text = fh.read()
    reader = csv.reader(io.StringIO(text, newline=''), delimiter=detect_delimiter(text), strict=True)
    try:
        header = [h.strip().lower() for h in next(reader)]
    except StopIteration:
        raise CsvError('arquivo vazio: sem cabeçalho')
    missing = [c for c in REQUIRED if c not in header]
    if missing:
        raise CsvError(f'colunas obrigatórias ausentes: {missing}')
    if len(set(header)) != len(header):
        raise CsvError(f'colunas duplicadas no cabeçalho: {header}')
    idx = {c: header.index(c) for c in REQUIRED}
    win_idx = header.index('winner') if 'winner' in header else None
    wins = defaultdict(set)                   # set de anos: S4
    while True:
        start = reader.line_num + 1
        try:
            row = next(reader)
        except StopIteration:
            break
        except csv.Error as e:
            raise CsvError(f'linha {start}: CSV malformado: {e}')
        if not row:                           # linha em branco
            continue
        if len(row) != len(header):           # menos (ou mais) colunas que o cabeçalho
            raise CsvError(f'linha {start}: {len(row)} colunas, esperado {len(header)}: {row!r}')
        year_s, prod_s = row[idx['year']].strip(), row[idx['producers']].strip()
        win_s = row[win_idx].strip() if win_idx is not None else ''
        if not re.fullmatch(r'\d+', year_s):
            raise CsvError(f'linha {start}: year inválido: {row!r}')
        names = producers(prod_s)
        if not names:
            raise CsvError(f'linha {start}: producers vazio: {row!r}')
        if win_s.lower() == 'yes':
            for p in names:
                wins[p].add(int(year_s))
    pairs = []
    for p, ys in wins.items():
        ys = sorted(ys)
        pairs += [(p, b - a, a, b) for a, b in zip(ys, ys[1:])]   # adjacentes: n anos => n-1 pares
    out = {'min': [], 'max': []}
    if pairs:
        lo = min(x[1] for x in pairs); hi = max(x[1] for x in pairs)
        fmt = lambda x: {'producer': x[0], 'interval': x[1], 'previousWin': x[2], 'followingWin': x[3]}
        order = sorted(pairs, key=lambda x: (x[2], x[0]))
        out['min'] = [fmt(x) for x in order if x[1] == lo]
        out['max'] = [fmt(x) for x in order if x[1] == hi]
    return out, wins

def distinct_wins(wins):
    """Vitórias distintas (produtor, ano), ordenadas por produtor e ano (contrato de movies.ProducerWins)."""
    return [(p, y) for p in sorted(wins) for y in sorted(wins[p])]

if __name__ == '__main__':
    try:
        out, wins = compute(sys.argv[1])
    except CsvError as e:
        print(f'ERRO: {e}', file=sys.stderr); sys.exit(2)
    if '--wins' in sys.argv:                      # TSV: produtor<TAB>ano
        for p, y in distinct_wins(wins): print(f'{p}\t{y}')
        sys.exit(0)
    print(json.dumps(out, indent=2, ensure_ascii=False))
    if '-v' in sys.argv:
        print('\n# produtores com vitórias:', len(wins), '| com 2+:', sum(len(v) > 1 for v in wins.values()))
        for p, ys in sorted(wins.items()):
            if len(ys) > 1: print(' ', p, sorted(ys))
