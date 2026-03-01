import { useCallback, useRef, useState } from 'react';

import useClickOutside from '@/hooks/useClickOutside';

import styles from './UserMenu.module.scss';

interface UserMenuProps {
  username: string;
  onLogout: () => void;
}

const UserMenu = ({ username, onLogout }: UserMenuProps) => {
  const [isOpen, setIsOpen] = useState(false);
  const menuRef = useRef<HTMLDivElement>(null);

  const close = useCallback(() => {
    setIsOpen(false);
  }, []);

  useClickOutside(menuRef, close);

  const handleKeyDown = (event: React.KeyboardEvent) => {
    if (event.key === 'Escape') {
      close();
    }
  };

  const handleToggle = () => {
    setIsOpen((prev) => !prev);
  };

  return (
    // eslint-disable-next-line jsx-a11y/no-static-element-interactions
    <div className={styles.userMenu} ref={menuRef} onKeyDown={handleKeyDown}>
      <span className={styles.username}>{username}</span>
      <button
        type='button'
        className={styles.avatarButton}
        onClick={handleToggle}
        aria-expanded={isOpen}
        aria-haspopup='menu'
      >
        <div className={styles.avatar}>{username.charAt(0)}</div>
      </button>

      {isOpen && (
        <div className={styles.dropdown} role='menu'>
          <button type='button' className={styles.dropdownItem} role='menuitem' disabled>
            Profile
          </button>
          <div className={styles.dropdownDivider} />
          <button type='button' className={styles.dropdownItem} role='menuitem' onClick={onLogout}>
            Logout
          </button>
        </div>
      )}
    </div>
  );
};

export default UserMenu;
