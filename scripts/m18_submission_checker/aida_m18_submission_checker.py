'''

'''
import sys, os, re, logging, tarfile, zipfile

'''
Globals
'''
# list of names of items in the archive - to be populated after determining what filetype the archive is
ARCHIVE_NAMES = []

warnings_dict = {'1a':".ttl files must be located in either the <run_ID>/NIST/ or <run_id>/INTER-TA directory",
                 '1b':".ttl files must be located the appropriate hypothesis_id subdirectory of the <run_ID>/NIST/ "
                      "directory. i.e. <run_id>/NIST/<hypothesis_id>/file.ttl",
                 '2':".ttl files must be located in either the <run_ID>/NIST/ or <run_id>/INTER-TA directory",
                 '3':".ttl files must be located in the <run_ID>/ directory" }



def check_runtime_args(args):
    """
    Checks the runtime arguments to ensure it is a file that exists. This function will log the errors detected, if any.
    :param args: runtime arguments (sys.argv)
    :return: True if valid runtime arguments, False otherwise
    """

    # check that an argument was given
    if len(args) < 2:
        logging.error("Missing archive file as argument.\nTo run: python3 aida_m18_submission_checker.py <path/to/archive>")
        return False

    # checks that argument is an existing file
    if not os.path.isfile(args[1]):
        logging.error("Argument is not a file.\nTo run: python3 aida_m18_submission_checker.py <path/to/archive>")
        return False

    return True

def get_valid_archive_filetype(archive_file_name):
    """
    Checks the archive file type. If archive is not of valid file type, log an error message.
    Valid filetypes: .tgz, .tar.gz, or .zip.

    :return: string with archive filetype 'tar' or 'zip', or None if invalid filetype
        (i.e. the library that should be used to read the archive)
    :rtype: string, or None
    """

    if archive_file_name.endswith('.tar.gz') or archive_file_name.endswith('.tgz'):
        return 'tar'
    elif archive_file_name.endswith('.zip'):
        return 'zip'
    else:
        logging.error("Archive filetype is unknown. Please use .zip, .tgz, or .tar.gz")
        return None

def get_archive_member_names(archive_file_name, archive_file_type):
    """
    Gets all of the file names/members of the archive.
    :param archive_file_name: the file name/path of the archive
    :param archive_file_type: the file extention type: 'tar' or 'zip'
    :return: a list of the names/members in the archive on success, None if failure to open/read archive
    :rtype: bool
    """

    logging.info("Checking archive... this may take a moment for large files.")

    try:
        archive = zipfile.ZipFile(archive_file_name, 'r', allowZip64=True) if archive_file_type == 'zip' else tarfile.open(archive_file_name, mode='r')
        member_names_list = archive.namelist() if archive_file_type == 'zip' else archive.getnames()
        return member_names_list
    except:
        # expected errors: (zipfile.BadZipFile, tarfile.ReadError), but catch all of them anyways
        logging.error("Error thrown attempting to read/open the archive. Please use 'zip' or 'tar' to create your archive.")
        return None

def get_task_type(archive_file_name, archive_member_names):
    """
    :param archive_member_names: a list of members/names in the archive
    :return: string corresponding to the task type, which could be any of the following: '1a', '1b', '2', or '3'
    :rtype: string
    """

    # count the number of .'s in the archive file name to determine the task type. Hope the user has the proper naming convention
    basename = os.path.basename(archive_file_name)

    dot_count = basename.count('.')

    # get rid of extra . count if the file extention is .tar.gz
    if basename.endswith('.tar.gz'):
        dot_count -= 1

    if dot_count == 1:
        # if theres only 1 dot_count, we need to check to see if it is 1a or 1b.

        member_names_str = " ".join(archive_member_names)

        # if .ttl file in "/NIST/" directory then ttls_under_nist_dir is not none (possible 1a)
        ttls_under_nist_dir = re.compile(".*\/NIST\/[^\/]+.ttl", re.IGNORECASE).search(member_names_str)
        # if ".ttl file in a subdirectory of /NIST/" then ttls_under_nist_subdir is not none (possible 1b)
        ttls_under_nist_subdir = re.compile(".*\/NIST\/\S*\/\S*\.ttl", re.IGNORECASE).search(member_names_str)

        if ttls_under_nist_dir is not None and ttls_under_nist_subdir is None:
            return '1a'
        elif ttls_under_nist_subdir is not None and ttls_under_nist_dir is None:
            return '1b'
        else:
            # ttls found in /NIST/ and a subdirectory of /NIST/, so we cannot determine if 1a or 1b.
            logging.warning("Cannot distinguish between 1a or 1b due to .ttl files existing at the same level as a subdirectory of /NIST/. \nContinuing as 1a.")
            return '1a'
            # ^^ change to return None if we want to stop processing rather than continue as 1a

    elif dot_count == 2:
        return '2'
    elif dot_count == 3:
        # else return the dot count if theres 2 or 3 dots
        return '3'

    else:
        logging.error("Cannot determine the task type! Please input task type to arguments and run again.")
        return None

def get_archive_submit_ready_status_values(task_type, archive_member_names):
    """
    Prereq: ARCHIVE_FILE_NAME, and ARCHIVE_NAMES have been set

    :return: returns dictionary with counts for total ttls found in archive, count of ttls in valid locations, and count of ttls in invalid locations
    :rtype: dict {'total': x, 'valid' : y, 'invalid' : z}
    """
    ttls_total_count = 0
    ttls_error_warning_count = 0
    ttls_valid_count = 0

    validity_check = None

    # function pointer for which type of validity checking we want to do
    if task_type == '1a' or task_type == '2':
        validity_check = do_TA1a_2_check
    elif task_type == '1b':
        validity_check = do_TA1b_check
    elif task_type == '3':
        validity_check = do_TA3_check

    for name in archive_member_names:

        # split into levels
        path_items = re.split('/', name)

        # TODO CHECK IF PATH_ITEMS[-1] IS EVER NOT THE LOCATION OF THE .TTL FILE NAME

        # if the current file is a .ttl file, check its validity and update counts
        if path_items[-1].endswith('.ttl'):

            ttls_total_count += 1

            file_status = validity_check(path_items)

            if file_status == True:
                ttls_valid_count += 1
            else:
                ttls_error_warning_count += 1

    return {'total' : ttls_total_count, 'valid' : ttls_valid_count, 'invalid' : ttls_error_warning_count}

def do_TA1a_2_check(path_items):
    """
    Check the path in the archive to ensure the path is valid
    TASK 1a Directory structure:
    <TA1aperformer>_<run>
        NIST
            <document_id>.ttl                  1a: (1 to X); 2: (1)
		INTER-TA
		    <document_id>.ttl                  1a: (0 to X); 2: (0 or 1)


    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    :param path_items: The path to the .ttl file that needs to be checked
    :return: True if the file path is valid, False otherwise
    :rtype: bool
    """

    # validate that the ttl file is in an acceptable directory
    # rule: ttl needs to be in 2nd level -- aka 3rd item in list['name', 'NIST', 'valid.ttl']
    # rule: ttl needs to be in directory named NIST, or INTER-TA
    try:
        if ((path_items[-2] == 'NIST' or path_items[-2] == 'INTER-TA') and len(path_items) == 3):
            return True
    except:
        return False
    return False


def do_TA1b_check(path_items):
    """
    Check the path in the archive to ensure the path is valid
    Task 1b directory structure:
    <TA1performer>_<run>
        NIST
            hypothesisID			(1 to Y)
                <document_id>.ttl 		(1 to X)

    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    :param path_items: The path to the .ttl file that needs to be checked
    :return: True if the file path is valid, False otherwise
    :rtype: bool
    """

    # validate that the ttl file is in an acceptable directory
    # rule: ttl needs to be in 3rd level -- aka 4th item in list['name', 'NIST', 'hypothesis', 'valid.ttl']
    # rule: ttl needs to be in a subdirectory within the NIST directory
    try:
        if path_items[-3] == 'NIST' and len(path_items) == 4:
            return True
    except:
        return False
    return False

def do_TA3_check(path_items):
    """
    Check the path in the archive to ensure the path is valid
    Task 3 directory structure:
    <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>(-<TA2performer>_<run>).<TA3performer>_<run>
        <TA1performer>_<run>(-<TA1performer>_<run>).<TA2performer>_<run>(-<TA2performer>_<run>).<TA3performer>_<run>.<SIN ID>.<SIN frame ID>.<H followed by three digits>.ttl			(1 to X)

    Ignores non ttl files.
    Prints number of ttl files found, and number of ttl files that are in need to be checked.

    we do not check for all NIST standards set by the document, only for basic directory structure.
    :param path_items: The path to the .ttl file that needs to be checked
    :return: True if the file path is valid, False otherwise
    :rtype: bool
    """

    # validate that the ttl file is in an acceptable directory
    # rule: ttl needs to be in 2nd level -- aka 3rd item in list['name', 'NIST', 'valid.ttl']
    # rule: ttl needs to be in directory named NIST, or INTER-TA

    return True if len(path_items) == 2 else False



def main():
    """
    :returns: True if script completed successfully (regardless if archive is submit ready or not)
              False if script ran into a fatal error during execution and did not complete successfully.
    :rtype: bool
    """

    # set logging to log/print INFO+ to stdout
    logging.basicConfig(level='INFO', format='%(levelname)s:  %(message)s')

    if check_runtime_args(sys.argv) is False:
        return False

    archive_file_name = str(sys.argv[1])

    archive_file_type = get_valid_archive_filetype(archive_file_name)

    if archive_file_type is None:
        return False

    archive_member_names = get_archive_member_names(archive_file_name, archive_file_type)

    if archive_member_names is None:
        return False

    task_type = get_task_type(archive_file_name, archive_member_names)
    if task_type == None:
        return False

    logging.info("Archive " + str(archive_file_name) + " is detected to be of task type " + task_type + ".")

    archive_status = get_archive_submit_ready_status_values(task_type, archive_member_names)

    # log archive report
    logging.info("M18 Submission Checker Report for: \n\t{0}".format(archive_file_name))
    logging.info("Total .ttl files found in submission archive: {0}".format(archive_status['total']))
    logging.info("Total number of .ttl files to be validated: {0}".format(archive_status['valid']))
    if archive_status['invalid'] > 0:
        logging.warning("Total number of .ttl files located in invalid locations and will not be validated: {0}".format(archive_status['invalid']))
        logging.warning(warnings_dict[task_type])
    elif archive_status['total'] > 0:   # dont want them accidentally submitting 0
        logging.info("{0} is VALID and submit ready according to task {1} rules!".format(archive_file_name, task_type))

    return True

if __name__ == "__main__": main()
