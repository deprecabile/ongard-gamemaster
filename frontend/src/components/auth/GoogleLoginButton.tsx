import { type CredentialResponse, GoogleLogin } from '@react-oauth/google';

import styles from './GoogleLoginButton.module.scss';

interface GoogleLoginButtonProps {
  onCredential: (credential: string) => void;
  onError: () => void;
}

const GoogleLoginButton = ({ onCredential, onError }: GoogleLoginButtonProps) => (
  <div className={styles.googleButtonWrapper}>
    <GoogleLogin
      onSuccess={(response: CredentialResponse) => {
        if (response.credential) {
          onCredential(response.credential);
        }
      }}
      onError={onError}
      theme='filled_black'
      size='large'
      shape='rectangular'
      text='signin_with'
      width='356'
      logo_alignment='left'
    />
  </div>
);

export default GoogleLoginButton;
