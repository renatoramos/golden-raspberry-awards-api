#!/usr/bin/env python3
"""CSV sintético no formato do oficial. 1.000.000 filmes, ~20% vencedores, 100.000 produtores,
30% dos filmes com 2 ou 3 produtores (separadores ' and ', ', ' e ', and' misturados). Semente fixa."""
import random, sys
N, PRODUCERS, SEED = 1_000_000, 100_000, 42
rnd = random.Random(SEED)
out = open(sys.argv[1], 'w', encoding='utf-8', newline='\n')
out.write('year;title;studios;producers;winner\n')
for i in range(1, N + 1):
    year = rnd.randint(1980, 2019)
    k = 1 if rnd.random() >= 0.30 else rnd.choice((2, 3))
    names = [f'Producer {p:06d}' for p in rnd.sample(range(1, PRODUCERS + 1), k)]
    if k == 1: prod = names[0]
    elif k == 2: prod = f'{names[0]} and {names[1]}'
    else: prod = rnd.choice((f'{names[0]}, {names[1]} and {names[2]}', f'{names[0]}, {names[1]}, and {names[2]}'))
    studio = f'Studio {rnd.randint(1, 500)}' if rnd.random() < 0.7 else f'Studio {rnd.randint(1, 500)}, Studio {rnd.randint(1, 500)}'
    out.write(f'{year};Film {i:07d};{studio};{prod};{"yes" if rnd.random() < 0.20 else ""}\n')
out.close()
