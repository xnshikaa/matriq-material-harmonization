import { useState } from 'react';
import type { FormEvent } from 'react';
import { ArrowRight, Eye, EyeOff, Layers3, LoaderCircle } from 'lucide-react';
import { MaterialScene } from '../components/MaterialScene';
import { api, ApiError } from '../services/api';
import type { Account } from '../types/workspace';
import './authentication.css';

export function Authentication({ onAuthenticated }: { onAuthenticated: (account: Account) => void }) {
  const [signup, setSignup] = useState(false);
  const [reviewerLogin, setReviewerLogin] = useState(false);
  const [shown, setShown] = useState(false);
  const [focus, setFocus] = useState('');
  const [email, setEmail] = useState('');
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState('');
  const sceneAttention = shown ? -1 : focus === 'password' || focus === 'confirmation' ? -.45 : focus ? 1 : 0;

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (busy) return;
    setError('');
    const fields = new FormData(event.currentTarget);
    const password = String(fields.get('password') ?? '');
    if (signup && password !== fields.get('confirmation')) {
      setError('The passwords do not match.');
      (event.currentTarget.elements.namedItem('confirmation') as HTMLInputElement).focus();
      return;
    }
    setBusy(true);
    try {
      await api.session();
      const session = await api.authenticate(signup ? 'signup' : reviewerLogin ? 'reviewer-login' : 'login', {
        name: signup ? String(fields.get('name') ?? '').trim() : undefined,
        email: email.trim(),
        password,
      });
      if (session.user) onAuthenticated(session.user);
    } catch (failure) {
      setError(failure instanceof TypeError || failure instanceof SyntaxError || (failure instanceof ApiError && failure.status >= 500)
        ? 'MATRIQ is starting or unavailable. Open it with run-frontend.bat and try again.'
        : failure instanceof Error ? failure.message : 'Sign-in could not be completed.');
    } finally {
      setBusy(false);
    }
  }

  return <main className="auth-shell">
    <div className="auth-stage" data-mode={signup ? "signup" : "login"}>
      <section className="auth-hero" aria-label="MATRIQ Material Intelligence">
        <MaterialScene signup={signup} attention={sceneAttention} />
        <div className="auth-brand"><span className="auth-mark"><Layers3 size={21} /></span> MATRIQ</div>
        <div className="auth-hero-copy"><h1>One Material.<br /><span>One Identity.</span></h1></div>
      </section>
      <section className="auth-content" aria-label="Account access">
        <div className="auth-card" key={`${signup}-${reviewerLogin}`}>
          <header>
            <h2>{signup ? 'Create your account' : reviewerLogin ? 'Reviewer sign in' : 'Sign in to MATRIQ'}</h2>
            <p>{signup ? 'Enter your details to create a standard workspace account.' : reviewerLogin ? 'Use your authorized reviewer account to record engineering decisions.' : 'Use your standard MATRIQ account to access the workspace.'}</p>
          </header>
          <form onSubmit={submit} aria-busy={busy}
            onFocus={event => setFocus(event.target instanceof HTMLInputElement ? event.target.name : '')}
            onBlur={event => { if (!event.currentTarget.contains(event.relatedTarget)) setFocus(''); }}>
            {signup && <label>Full name<input name="name" autoComplete="name" maxLength={100} required /></label>}
            <label>Email address<input type="email" name="email" autoComplete="username" value={email}
              onChange={event => setEmail(event.target.value)} maxLength={254} required /></label>
            <div className="auth-password-field">
              <label htmlFor="auth-password">Password</label>
              <div className="auth-password">
                <input id="auth-password" type={shown ? 'text' : 'password'} name="password"
                  autoComplete={signup ? 'new-password' : 'current-password'} minLength={signup ? 12 : undefined} maxLength={128}
                  aria-describedby={signup ? 'password-hint' : undefined} required />
                <button type="button" aria-label={shown ? 'Hide password' : 'Show password'} onClick={() => setShown(!shown)}>
                  {shown ? <EyeOff size={18} /> : <Eye size={18} />}
                </button>
              </div>
              {signup && <small id="password-hint">Use at least 12 characters.</small>}
            </div>
            {signup && <label>Confirm password<input type={shown ? 'text' : 'password'} name="confirmation"
              autoComplete="new-password" minLength={12} maxLength={128} required /></label>}
            {error && <p role="alert" className="auth-error">{error}</p>}
            <button className="auth-submit" type="submit" disabled={busy}>
              {busy ? 'Please wait…' : signup ? 'Create Account' : reviewerLogin ? 'Sign In as Reviewer' : 'Sign In'}
              {busy ? <LoaderCircle className="auth-spinner" size={18} /> : <ArrowRight size={18} />}
            </button>
          </form>
          {!reviewerLogin && <p className="auth-switch">{signup ? 'Already have an account?' : 'New to MATRIQ?'}{' '}
            <button type="button" disabled={busy} onClick={() => { setSignup(!signup); setError(''); setShown(false); setFocus(''); }}>
              {signup ? 'Sign In' : 'Create Account'}
            </button>
          </p>}
          {!signup && <p className="auth-switch"><button type="button" disabled={busy} onClick={() => { setReviewerLogin(!reviewerLogin); setError(''); setShown(false); }}>
            {reviewerLogin ? 'Standard user sign in' : 'Reviewer sign in'}
          </button></p>}
        </div>
      </section>
    </div>
  </main>;
}
