export type SearchMethod = 'exact' | 'substring' | 'subsequence';
export interface SearchMatch { score: number; method: SearchMethod }

export function normalizeSearchText(value: string) {
  return value.normalize('NFKD').replace(/[\u0300-\u036f]/g, '').toLowerCase()
    .replace(/[^a-z0-9]+/g, ' ').trim().replace(/\s+/g, ' ');
}

function longestCommonSubstring(a: string, b: string): number {
  let previous = new Array<number>(b.length + 1).fill(0);
  let best = 0;
  for (let i = 1; i <= a.length; i++) {
    const current = new Array<number>(b.length + 1).fill(0);
    for (let j = 1; j <= b.length; j++) {
      if (a[i - 1] === b[j - 1]) {
        current[j] = previous[j - 1] + 1;
        best = Math.max(best, current[j]);
      }
    }
    previous = current;
  }
  return best;
}

function longestCommonSubsequence(a: string, b: string): number {
  let previous = new Array<number>(b.length + 1).fill(0);
  for (let i = 1; i <= a.length; i++) {
    const current = new Array<number>(b.length + 1).fill(0);
    for (let j = 1; j <= b.length; j++) {
      current[j] = a[i - 1] === b[j - 1]
        ? previous[j - 1] + 1
        : Math.max(previous[j], current[j - 1]);
    }
    previous = current;
  }
  return previous[b.length];
}

function compareToken(query: string, candidate: string): SearchMatch {
  if (query === candidate) return { score: 1, method: 'exact' };
  const denominator = Math.max(query.length, candidate.length);
  const substring = longestCommonSubstring(query, candidate) / denominator;
  const subsequence = longestCommonSubsequence(query, candidate) / denominator;
  return subsequence > substring
    ? { score: subsequence, method: 'subsequence' }
    : { score: substring, method: 'substring' };
}

/** Fuzzy relevance from exact phrases, longest common substring, and longest common subsequence. */
export function materialSearchMatch(query: string, value: string): SearchMatch {
  const needle = normalizeSearchText(query);
  const text = normalizeSearchText(value);
  if (!needle) return { score: 1, method: 'exact' };
  if (!text) return { score: 0, method: 'substring' };
  if (text === needle) return { score: 1, method: 'exact' };
  if (text.includes(needle)) return { score: 0.98, method: 'substring' };

  const queryTokens = needle.split(' ');
  const textTokens = text.split(' ');
  const matches = queryTokens.map(token => textTokens.reduce((best, candidate) => {
    const score = compareToken(token, candidate);
    return score.score > best.score ? score : best;
  }, { score: 0, method: 'substring' as SearchMethod }));
  const score = matches.reduce((sum, match) => sum + match.score, 0) / matches.length;
  const method = matches.reduce((best, match) => match.score > best.score ? match : best, matches[0]).method;
  return { score, method };
}

export function bestMaterialSearchMatch(query: string, values: string[]): SearchMatch {
  return values.reduce((best, value) => {
    const match = materialSearchMatch(query, value);
    return match.score > best.score ? match : best;
  }, { score: 0, method: 'substring' as SearchMethod });
}

export function searchMethodLabel(method: SearchMethod) {
  return method === 'exact' ? 'exact text' : `longest common ${method}`;
}
